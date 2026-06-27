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

package io.github.autotweaker.core.infrastructure.config

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persistence.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persistence.ProviderStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

object ProviderConfigAPI : ProviderRepository, Loggable, Traceable {
	private val apiKeyConfig = ApiKeyConfigAPI
	private val modelConfig: ModelConfigRepository = ModelConfigAPI
	private val store = ProviderStore
	
	private val mutex = Mutex()
	
	override fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
	override fun getMeta(type: String) = LlmClientLoader.load(type).providerInfo
	
	override suspend fun list() = store.getAll().values.map { it.toCoreConfig() }
	
	override suspend fun get(id: UUID): CoreConfig.ProviderConfig.Provider? =
		store.get(id)?.toCoreConfig()
	
	override suspend fun remove(id: UUID) = mutex.withLock {
		val modelIds = modelConfig.list().filter { it.data.providerId == id }.map { it.data.id }
		val defaultModel = ModelResolverImpl.getDefaultModel()
		require(defaultModel !in modelIds) { "Cannot delete provider: contains default model $defaultModel" }
		modelIds.forEach { modelConfig.remove(it) }
		return@withLock store.delete(id).andLog(log) {
			info("Deleted provider  id={}  modelCount={}", id, modelIds.count())
		}
	}
	
	override suspend fun set(provider: CoreConfig.ProviderConfig.Provider) = mutex.withLock {
		check(store.getAll().values.all { it.displayName != provider.displayName })
		val meta = LlmClientLoader.load(provider.type).providerInfo
		store.set(
			ProviderData(
				id = provider.id,
				displayName = provider.displayName,
				providerType = provider.type,
				apiKey = apiKeyConfig.getId(provider.keyId),
				baseUrl = provider.baseUrl ?: meta.baseUrl,
				errorHandlingRules = provider.errorHandlingRules ?: meta.errorHandlingRules,
			)
		).andLog(log) {
			info("Created provider  id={}  type={}  name={}", provider.id, provider.type, provider.displayName)
		}
	}
	
	private suspend fun ProviderData.toCoreConfig() = CoreConfig.ProviderConfig.Provider(
		id = id,
		type = providerType,
		keyId = apiKeyConfig.getName(apiKey) ?: "unknown",
		baseUrl = baseUrl,
		displayName = displayName,
		errorHandlingRules = errorHandlingRules,
	)
}
