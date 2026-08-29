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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.store.ImmutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.notfound.ModelNotFoundException
import io.github.autotweaker.api.types.exception.notfound.ProviderNotFoundException
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.agent.RuntimeProvider
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.nullable
import java.util.*

class ModelResolverImpl(private val secret: SecretStore) : ImmutableStore<UUID?>(), ModelResolver, Loggable {
	override val serializer = UuidSerializer.nullable
	override fun default() = null
	
	fun getDefaultModel(): UUID? = cache.get()
	
	suspend fun <R> getDefaultModel(function: suspend (UUID?) -> R) = cache.get(function)
	
	suspend fun setDefaultModel(id: UUID?) {
		if (id == null) {
			cache.set(null)
			log.info("Cleared default model")
			return
		}
		cache.update {
			ModelStore.get(id) ?: throw ModelNotFoundException(id)
			return@update id
		}
		log.info("Set default model  modelId={}", id)
	}
	
	override suspend fun resolve(id: UUID): RuntimeModel {
		val resolvedId = resolveModelId(id)
		val model = ModelStore.get(resolvedId) ?: throw ModelNotFoundException(id)
		val provider = ProviderStore.get(model.providerId) ?: throw ProviderNotFoundException(model.providerId)
		return RuntimeModel(
			id = model.id,
			provider = RuntimeProvider(
				id = provider.id,
				name = provider.providerType,
				baseUrl = provider.baseUrl,
				apiKey = secret.get(provider.apiKey),
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
		for ((key, value) in ModelStore.getAll())
			if (ProviderStore.get(value.providerId) != null)
				return key.andLog(log) {
					warn("Resolved model via fallback  requestedId={}  fallbackId={}", id, key)
				}
		
		return id
	}
	
	private suspend fun UUID.available(): Boolean {
		val model = ModelStore.get(this) ?: return false
		ProviderStore.get(model.providerId) ?: return false
		return true
	}
}
