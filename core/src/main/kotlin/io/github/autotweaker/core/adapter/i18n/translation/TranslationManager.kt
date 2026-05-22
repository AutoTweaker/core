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
import io.github.autotweaker.core.data.json.JsonStoreImpl
import io.github.autotweaker.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.*

@Suppress("unused")
object TranslationManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
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
		if (TranslationEngine.isLanguageCovered(target)) {
			logger.info("Translations already complete for target  target={}  action=skip", target.toLanguageTag())
			return
		}
		
		_status.value = TranslationStatus.TRANSLATING
		CoroutineScope(Dispatchers.Default).launch {
			try {
				TranslationEngine.run(svc, modelId, target, sessionStore)
			} catch (e: Exception) {
				logger.error("Failed to translate  target={}", target.toLanguageTag(), e)
			} finally {
				_status.value = TranslationStatus.IDLE
			}
		}
		logger.info("Translation started  target={}  modelId={}", target.toLanguageTag(), modelId)
	}
	
	private fun loadData(): TranslationData {
		val element = jsonEntry.get() ?: return TranslationData()
		return Json.decodeFromJsonElement(element)
	}
	
	private fun saveData(data: TranslationData) {
		jsonEntry.set(Json.encodeToJsonElement(data))
	}
}
