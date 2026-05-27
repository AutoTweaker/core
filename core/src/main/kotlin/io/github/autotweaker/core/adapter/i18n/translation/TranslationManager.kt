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
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

object TranslationManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(this::class)

	private lateinit var modelRepo: ModelRepository
	private lateinit var settings: SettingService
	private lateinit var i18nService: I18nService

	fun init(
		modelRepo: ModelRepository,
		settings: SettingService,
		i18nService: I18nService,
	) {
		this.modelRepo = modelRepo
		this.settings = settings
		this.i18nService = i18nService
	}

	val status: StateFlow<TranslationStatus> get() = _status.asStateFlow()
	private val _status = MutableStateFlow(TranslationStatus.IDLE)
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	fun getModel(): UUID? = loadModelId()

	fun setModel(modelId: UUID?) {
		saveModelId(modelId)
		logger.debug("Translation model updated  modelId={}", modelId)
	}

	fun startTranslation() {
		if (!_status.compareAndSet(TranslationStatus.IDLE, TranslationStatus.TRANSLATING)) {
			logger.debug("Translation already in progress  action=skip")
			return
		}

		val modelId = loadModelId()
		if (modelId == null) {
			logger.info("Translation model not configured  action=skip")
			_status.value = TranslationStatus.IDLE
			return
		}

		val target = i18nService.getLanguage()
		if (TranslationEngine.isLanguageCovered(target, i18nService)) {
			logger.info("Translations already complete for target  target={}  action=skip", target.toLanguageTag())
			_status.value = TranslationStatus.IDLE
			return
		}

		scope.launch {
			try {
				TranslationEngine.run(settings, modelId, target, modelRepo, i18nService)
			} catch (e: Exception) {
				logger.error("Failed to translate  target={}", target.toLanguageTag(), e)
			} finally {
				_status.value = TranslationStatus.IDLE
			}
		}
		logger.info("Translation started  target={}  modelId={}", target.toLanguageTag(), modelId)
	}

	fun shutdown() {
		scope.cancel()
	}

	private fun loadModelId(): UUID? {
		val element = jsonEntry.get() ?: return null
		return Json.decodeFromJsonElement(UuidSerializer.nullable, element)
	}

	private fun saveModelId(modelId: UUID?) {
		jsonEntry.set(Json.encodeToJsonElement(UuidSerializer.nullable, modelId))
	}
}
