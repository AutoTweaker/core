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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import java.util.*

object ModelResolverImpl : ModelResolver, Loggable, JsonStorable {
	private lateinit var secretStore: SecretStore
	
	fun init(secretStore: SecretStore) {
		this.secretStore = secretStore
	}
	
	fun getDefaultModel(): UUID? =
		store.get()?.let { Json.decodeFromJsonElement(UuidSerializer.nullable, it) }
	
	suspend fun setDefaultModel(id: UUID) {
		requireNotNull(ModelStore.get(id)) { "Model not found: $id" }
		store.set(Json.encodeToJsonElement(UuidSerializer.nullable, id))
		log.info("Set default model  modelId={}", id)
	}
	
	override suspend fun resolve(id: UUID): Model? {
		val resolvedId = resolveModelId(id)
		val model = ModelStore.get(resolvedId) ?: return null
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
	
	private suspend fun resolveModelId(id: UUID): UUID {
		if (id.available()) return id
		val defaultId = getDefaultModel()
		if (defaultId?.available() == true) {
			log.warn("Resolved model via default  requestedId={}  defaultId={}", id, defaultId)
			return defaultId
		}
		for (model in ModelStore.getAll())
			if (ProviderStore.get(model.value.providerId) != null)
				return model.key.andLog(log) {
					warn("Resolved model via fallback  requestedId={}  fallbackId={}", id, model.key)
				}
		
		return id
	}
	
	private suspend fun UUID.available(): Boolean {
		val model = ModelStore.get(this) ?: return false
		ProviderStore.get(model.providerId) ?: return false
		return true
	}
}
