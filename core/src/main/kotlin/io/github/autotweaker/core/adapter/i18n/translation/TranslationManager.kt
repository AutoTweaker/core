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
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.ModelRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import java.util.*

object TranslationManager : Loggable, Traceable, JsonStorable, Settable {
	
	private lateinit var modelRepo: ModelRepository
	private lateinit var i18nService: I18nService
	
	fun init(
		modelRepo: ModelRepository,
		i18nService: I18nService,
	) {
		this.modelRepo = modelRepo
		this.i18nService = i18nService
	}
	
	val status: StateFlow<TranslationStatus> get() = _status.asStateFlow()
	private val _status = MutableStateFlow(TranslationStatus.IDLE)
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	fun getModel(): UUID? = loadModelId()
	
	fun setModel(modelId: UUID?) {
		saveModelId(modelId)
		log.debug("Updated translation model  modelId={}", modelId)
	}
	
	fun startTranslation() {
		if (!_status.compareAndSet(TranslationStatus.IDLE, TranslationStatus.TRANSLATING)) {
			log.debug("Skipped translation  reason=already_in_progress")
			return
		}
		
		val modelId = loadModelId()
		if (modelId == null) {
			log.info("Skipped translation  reason=model_not_configured")
			_status.value = TranslationStatus.IDLE
			return
		}
		
		val target = i18nService.getLanguage()
		if (TranslationEngine.isLanguageCovered(target, i18nService)) {
			log.info("Skipped translation  reason=already_complete  target={}  action=skip", target.toLanguageTag())
			_status.value = TranslationStatus.IDLE
			return
		}
		
		scope.launch {
			try {
				TranslationEngine.run(modelId, target, modelRepo, i18nService)
			} catch (e: CancellationException) {
				trace.exception(e)
				throw e
			} catch (e: Exception) {
				trace.exception(e)
				log.error("Failed translation  target={}", target.toLanguageTag(), e)
			} finally {
				_status.value = TranslationStatus.IDLE
			}
		}
		log.info("Started translation  target={}  modelId={}", target.toLanguageTag(), modelId)
	}
	
	fun shutdown() {
		scope.cancel()
	}
	
	private fun loadModelId(): UUID? {
		val element = store.get() ?: return null
		return Json.decodeFromJsonElement(UuidSerializer.nullable, element)
	}
	
	private fun saveModelId(modelId: UUID?) {
		store.set(Json.encodeToJsonElement(UuidSerializer.nullable, modelId))
	}
}
