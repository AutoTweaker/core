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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrDefault
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.CoreLlmRequest
import io.github.autotweaker.core.adapter.i18n.I18nRegistry
import io.github.autotweaker.core.application.chat.ChatService
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.ModelResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import java.util.*
import kotlin.time.Clock

object TranslationEngine : Loggable, Traceable, Settable, I18nable {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
	
	data class BatchJob(
		val model: Model,
		val systemPrompt: String,
		val userPromptTemplate: String,
		val target: Locale,
		val batch: List<TranslationUnit>,
	)
	
	private data class BatchResult(
		val translated: Map<String, String>,
		val batch: List<TranslationUnit>,
		val target: Locale,
	)
	
	suspend fun run(
		modelId: UUID, target: Locale,
		modelRepo: ModelResolver,
	) {
		val model = modelRepo.resolve(modelId) ?: error("Model not found: $modelId")
		val systemPrompt =
			setting.get(TranslateSettings.SystemPrompt()).value.replace("{{target_language}}", target.displayName)
		val userPromptTemplate = setting.get(TranslateSettings.UserPrompt()).value
		val batchSize = setting.get(TranslateSettings.BatchSize()).value
		
		val units = collectUnits(target)
		if (units.isEmpty()) return
		
		val jobs = units.chunked(batchSize).map {
			BatchJob(model, systemPrompt, userPromptTemplate, target, it)
		}
		val semaphore = Semaphore(setting.get(TranslateSettings.MaxConcurrent()).value)
		
		coroutineScope {
			jobs.map { job ->
				async {
					semaphore.withPermit {
						val result = translateBatch(job)
						persistResults(result)
					}
				}
			}.awaitAll()
		}
		
		log.info("Completed translation  target={}  keys={}", target.toLanguageTag(), units.size)
	}
	
	private fun collectUnits(target: Locale): List<TranslationUnit> {
		val persisted = i18n.getAll().associateBy { it.key }
		return I18nRegistry.getAll().mapNotNull { (key, def) ->
			val alreadyHas = persisted[key]?.localizations?.any { it.languageCode == target } == true
			if (alreadyHas) null
			else TranslationUnit(key, def.localizations.map { it.text })
		}
	}
	
	fun isLanguageCovered(target: Locale): Boolean {
		val persisted = i18n.getAll().associateBy { it.key }
		return I18nRegistry.getAll().keys.all { key ->
			persisted[key]?.localizations?.any { it.languageCode == target } == true
		}
	}
	
	private suspend fun translateBatch(job: BatchJob): BatchResult {
		val contentJson = buildContentJson(job.batch)
		val userPrompt = job.userPromptTemplate.replace("{{target_language}}", job.target.displayName)
			.replace("{{content_to_translate}}", contentJson)
		
		val request = CoreLlmRequest(
			model = job.model.id,
			fallbackModels = null,
			messages = listOf(
				ChatMessage.SystemMessage(job.systemPrompt, Clock.System.now()),
				ChatMessage.UserMessage(userPrompt, Clock.System.now()),
			),
			stream = false,
			thinking = setting.get(TranslateSettings.Thinking()).value,
			responseFormat = ChatRequest.ResponseFormat(ChatRequest.ResponseFormat.Type.JSON_OBJECT)
		)
		val results = ChatService.chat(request).toList()
		
		val finalResult =
			results.filter { it.result.message !is ChatMessage.ErrorMessage }.map { it.result }.lastOrNull()
				?: return BatchResult(
					emptyMap(), job.batch, job.target
				)
		if (finalResult.message is ChatMessage.ErrorMessage) return BatchResult(emptyMap(), job.batch, job.target)
		
		val text = (finalResult.message as? ChatMessage.AssistantMessage)?.content
			?: return BatchResult(
				emptyMap(), job.batch, job.target
			)
		return BatchResult(parseResponse(text), job.batch, job.target)
	}
	
	private fun persistResults(r: BatchResult) {
		for ((key, value) in r.translated) {
			if (value.isBlank()) continue
			val sourceText = r.batch.find { it.key == key }?.localizations?.firstOrNull() ?: ""
			if (PlaceholderValidator.validate(sourceText, value)) {
				trace.catching { i18n.set(key, value, r.target) }
					.onFailure { log.warn("Failed translation persistence  key={}  reason={}", key, it.message) }
			} else {
				log.warn(
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
		return trace.catching {
			val obj = json.parseToJsonElement(jsonText).jsonObject
			obj.mapNotNull { (k, v) -> v.jsonPrimitive.content.let { k to it } }.toMap()
		}.onFailure { log.warn("Failed translation response parsing  length={}", responseText.length) }
			.getOrDefault(emptyMap())
	}
}
