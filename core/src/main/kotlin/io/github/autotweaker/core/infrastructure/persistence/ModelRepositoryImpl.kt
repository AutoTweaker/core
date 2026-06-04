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

package io.github.autotweaker.core.infrastructure.persistence

import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

object ModelRepositoryImpl : ModelRepository {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry by lazy { JsonStoreImpl.namespace(this::class) }
	
	private lateinit var secretStore: SecretStore
	
	fun init(secretStore: SecretStore) {
		this.secretStore = secretStore
	}
	
	fun getDefaultModel(): UUID? {
		val element = jsonEntry.get() ?: return null
		return Json.decodeFromJsonElement(UuidSerializer.nullable, element)
	}
	
	fun setDefaultModel(id: UUID) {
		if (ModelStore.get(id) == null) error("Model not found: $id")
		jsonEntry.set(Json.encodeToJsonElement(UuidSerializer.nullable, id))
		logger.info("Set default model  modelId={}", id)
	}
	
	override suspend fun resolve(id: UUID): Model? {
		val actualId = resolveModelId(id)
		val model = ModelStore.get(actualId) ?: return null
		val provider = ProviderStore.get(model.providerId) ?: return null
		return Model(
			id = model.id,
			provider = Provider(
				id = provider.id,
				name = provider.providerType,
				baseUrl = provider.baseUrl,
				apiKey = secretStore.get(provider.apiKey),
				errorHandlingRules = provider.errorHandlingRules,
			),
			modelInfo = model.modelInfo,
			config = model.config,
		)
	}
	
	private fun resolveModelId(id: UUID): UUID {
		if (ModelStore.get(id) != null) return id
		val defaultId = getDefaultModel()
		if (defaultId != null && ModelStore.get(defaultId) != null) {
			logger.warn("Resolved model via default  requestedId={}  defaultId={}", id, defaultId)
			return defaultId
		}
		val first = ModelStore.get().firstOrNull()
		if (first != null) {
			logger.warn("Resolved model via fallback  requestedId={}  fallbackId={}", id, first.id)
			return first.id
		}
		return id
	}
}
