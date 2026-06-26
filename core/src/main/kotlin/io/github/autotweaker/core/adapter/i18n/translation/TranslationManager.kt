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
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.i18n.TranslationStatus
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.ModelResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import java.util.*

object TranslationManager : Loggable, Traceable, JsonStorable, I18nable {
	
	private lateinit var modelRepo: ModelResolver
	
	fun init(
		modelRepo: ModelResolver,
	) {
		this.modelRepo = modelRepo
	}
	
	private val _status = MutableStateFlow(TranslationStatus.IDLE)
	val status: StateFlow<TranslationStatus> get() = _status.asStateFlow()
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	fun getModel(): UUID? = loadModelId()
	
	fun setModel(modelId: UUID?) {
		saveModelId(modelId)
		log.debug("Updated translation model  modelId={}", modelId)
	}
	
	fun startTranslation(): Boolean {
		val logSkipped: (reason: String) -> Unit = {
			log.debug("Skipped translation  reason={}", it)
		}
		
		if (!_status.compareAndSet(TranslationStatus.IDLE, TranslationStatus.TRANSLATING)) {
			logSkipped("already-in-progress")
			return false
		}
		
		val modelId = loadModelId()
		if (modelId == null) {
			logSkipped("model-not-configured")
			_status.value = TranslationStatus.IDLE
			return false
		}
		
		val target = i18n.getLanguage()
		if (TranslationEngine.isLanguageCovered(target)) {
			logSkipped("already-complete")
			_status.value = TranslationStatus.IDLE
			return false
		}
		
		scope.launch {
			log.info("Started translation  target={}  modelId={}", target.toLanguageTag(), modelId)
			trace.catching {
				TranslationEngine.run(modelId, target, modelRepo)
			}.rethrowCancellation()
				.onFailure { log.error("Failed translation  target={}", target.toLanguageTag(), it) }
				.also { _status.value = TranslationStatus.IDLE }
				.getOrThrow()
		}
		
		return true
	}
	
	fun shutdown() {
		scope.cancel()
	}
	
	private fun loadModelId(): UUID? =
		store.get()?.let { Json.decodeFromJsonElement(UuidSerializer.nullable, it) }
	
	
	private fun saveModelId(modelId: UUID?) =
		store.set(Json.encodeToJsonElement(UuidSerializer.nullable, modelId))
}
