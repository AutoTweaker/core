/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.adapter.i18n.translation

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.adapter.i18n.I18nRegistry
import io.github.autotweaker.core.adapter.i18n.I18nServiceImpl
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.ResilientChat
import io.github.autotweaker.core.data.json.JsonStoreImpl
import io.github.autotweaker.core.session.ProviderService
import io.github.autotweaker.core.session.SessionStore
import io.github.autotweaker.core.session.UsageStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock

@Suppress("unused")
object TranslationManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
	private val jsonEntry = JsonStoreImpl.namespace(TranslationManager::class.java.name)
	private val sessionStore: SessionStore by lazy {
		ServiceLoader.load(SessionStore::class.java).firstOrNull() ?: error("No SessionStore implementation found")
	}
	
	val status: StateFlow<TranslationStatus> get() = _status.asStateFlow()
	private val _status = MutableStateFlow(TranslationStatus.IDLE)
	
	fun getModel(): UUID? = loadData().modelId
	
	fun getLanguage(): Locale? = loadData().target
	
	fun updateModel(modelId: UUID) {
		val current = loadData()
		saveData(current.copy(modelId = modelId))
		logger.debug("Translation model updated  modelId={}", modelId)
	}
	
	fun updateLanguage(locale: Locale) {
		val current = loadData()
		saveData(current.copy(target = locale))
		logger.debug("Translation target updated  target={}", locale.toLanguageTag())
	}
	
	fun startTranslation(svc: SettingService) {
		if (_status.value == TranslationStatus.TRANSLATING) {
			logger.debug("Translation already in progress  action=skip")
			return
		}
		
		val data = loadData()
		val modelId = data.modelId
		if (modelId == null) {
			logger.info("Translation model not configured  action=skip")
			return
		}
		
		val target = data.target ?: Locale.getDefault()
		if (isLanguageCovered(target)) {
			logger.info("Translations already complete for target  target={}  action=skip", target.toLanguageTag())
			return
		}
		
		_status.value = TranslationStatus.TRANSLATING
		CoroutineScope(Dispatchers.Default).launch {
			try {
				doTranslate(svc, modelId, target)
			} catch (e: Exception) {
				logger.error("Failed to translate  target={}", target.toLanguageTag(), e)
			} finally {
				_status.value = TranslationStatus.IDLE
			}
		}
		logger.info("Translation started  target={}  modelId={}", target.toLanguageTag(), modelId)
	}
	
	private data class BatchJob(
		val model: Model,
		val svc: SettingService,
		val systemPrompt: String,
		val userPromptTemplate: String,
		val target: Locale,
		val batch: List<TranslationUnit>,
	)
	
	private suspend fun doTranslate(cfg: SettingService, modelId: UUID, target: Locale) {
		val model = ProviderService.getModel(modelId) ?: throw IllegalStateException("Model not found: $modelId")
		val systemPrompt =
			cfg.get(TranslateSettings.SystemPrompt()).value.replace("{{target_language}}", target.displayName)
		val userPromptTemplate = cfg.get(TranslateSettings.UserPrompt()).value
		val batchSize = cfg.get(TranslateSettings.BatchSize()).value
		
		val units = collectUnits(target)
		if (units.isEmpty()) return
		
		val jobs = units.chunked(batchSize).map {
			BatchJob(model, cfg, systemPrompt, userPromptTemplate, target, it)
		}
		val semaphore = Semaphore(cfg.get(TranslateSettings.MaxConcurrent()).value)
		
		coroutineScope {
			jobs.map { job ->
				async {
					semaphore.withPermit { translateBatch(job) }
				}
			}.awaitAll().forEach { r ->
				persistResults(r)
			}
		}
		
		logger.info("Translation completed  target={}  keys={}", target.toLanguageTag(), units.size)
	}
	
	private fun collectUnits(target: Locale): List<TranslationUnit> {
		val persisted = I18nServiceImpl.getAll().associateBy { it.key }
		return I18nRegistry.getAll().mapNotNull { (key, def) ->
			val alreadyHas = persisted[key]?.localizations?.any { it.languageCode == target } == true
			if (alreadyHas) null
			else TranslationUnit(key, def.localizations.map { it.text })
		}
	}
	
	private data class BatchResult(
		val translated: Map<String, String>,
		val batch: List<TranslationUnit>,
		val target: Locale,
	)
	
	private suspend fun translateBatch(job: BatchJob): BatchResult {
		val contentJson = buildContentJson(job.batch)
		val userPrompt = job.userPromptTemplate.replace("{{target_language}}", job.target.displayName)
			.replace("{{content_to_translate}}", contentJson)
		
		val results = ResilientChat.execute(
			model = job.model,
			fallbackModels = null,
			messages = listOf(
				ChatMessage.SystemMessage(job.systemPrompt, Clock.System.now()),
				ChatMessage.UserMessage(userPrompt, Clock.System.now()),
			),
			stream = false,
			thinking = false,
			service = job.svc,
		).toList()
		
		val finalResult = results.filter { it.retrying == null }.map { it.result }.lastOrNull() ?: return BatchResult(
			emptyMap(), job.batch, job.target
		)
		if (finalResult.message is ChatMessage.ErrorMessage) return BatchResult(emptyMap(), job.batch, job.target)
		finalResult.usage?.let { usage ->
			val record = SessionMessage.UsageRecord(
				UUID.randomUUID(), Clock.System.now(), UsageSnapshot(usage, job.model.modelInfo)
			)
			UsageStore.collect(listOf(record))
			sessionStore.saveMessages(listOf(record))
		}
		
		val text = (finalResult.message as? ChatMessage.AssistantMessage)?.content ?: return BatchResult(
			emptyMap(), job.batch, job.target
		)
		return BatchResult(parseResponse(text), job.batch, job.target)
	}
	
	private fun persistResults(r: BatchResult) {
		for ((key, value) in r.translated) {
			if (value.isBlank()) continue
			val sourceText = r.batch.find { it.key == key }?.localizations?.firstOrNull() ?: ""
			if (PlaceholderValidator.validate(sourceText, value)) {
				try {
					I18nServiceImpl.set(key, value, r.target)
				} catch (e: Exception) {
					logger.warn("Failed to persist translation  key={}  error={}", key, e.message)
				}
			} else {
				logger.warn(
					"Placeholder validation failed  key={}  source={}  translated={}", key, sourceText, value
				)
			}
		}
	}
	
	private fun buildContentJson(units: List<TranslationUnit>): String {
		val map = units.associate { unit ->
			unit.key to Json.encodeToJsonElement(unit.localizations)
		}
		return json.encodeToString(JsonObject.serializer(), JsonObject(map))
	}
	
	private fun parseResponse(responseText: String): Map<String, String> {
		val start = responseText.indexOf('{')
		val end = responseText.lastIndexOf('}')
		if (start == -1 || end == -1 || start >= end) return emptyMap()
		
		val jsonText = responseText.substring(start, end + 1)
		return try {
			val obj = json.parseToJsonElement(jsonText).jsonObject
			obj.mapNotNull { (k, v) ->
				v.jsonPrimitive.content.let { k to it }
			}.toMap()
		} catch (_: Exception) {
			logger.warn("Failed to parse translation response  length={}", responseText.length)
			emptyMap()
		}
	}
	
	private fun isLanguageCovered(target: Locale): Boolean {
		val persisted = I18nServiceImpl.getAll().associateBy { it.key }
		return I18nRegistry.getAll().keys.all { key ->
			persisted[key]?.localizations?.any { it.languageCode == target } == true
		}
	}
	
	private fun loadData(): TranslationData {
		val element = jsonEntry.get() ?: return TranslationData()
		return json.decodeFromJsonElement(element)
	}
	
	private fun saveData(data: TranslationData) {
		jsonEntry.set(json.encodeToJsonElement(data))
	}
}
