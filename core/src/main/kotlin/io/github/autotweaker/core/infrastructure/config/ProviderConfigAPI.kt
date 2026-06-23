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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.getOrDefault
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persistence.ModelRepositoryImpl
import io.github.autotweaker.core.infrastructure.persistence.ProviderStore
import java.util.*

object ProviderConfigAPI : ProviderRepository, Loggable, Traceable {
	private val apiKeyConfig = ApiKeyConfigAPI
	private val modelConfig: ModelConfigRepository = ModelConfigAPI
	private val store = ProviderStore
	
	override fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
	override fun getMeta(type: String) = LlmClientLoader.load(type).providerInfo
	override fun list() = store.get().map {
		CoreConfig.ProviderConfig.Provider(
			id = it.id,
			type = it.providerType,
			keyId = trace.catching {
				apiKeyConfig.getName(it.apiKey)
			}.rethrowNot<IllegalStateException>()
				.getOrDefault("unknown"),
			baseUrl = it.baseUrl,
			displayName = it.displayName,
			errorHandlingRules = it.errorHandlingRules,
		)
	}
	
	override fun delete(id: UUID) {
		val modelIds = modelConfig.list().filter { it.data.providerId == id }.map { it.data.id }
		val defaultModel = ModelRepositoryImpl.getDefaultModel()
		if (defaultModel != null && defaultModel in modelIds) error("Cannot delete provider: contains default model $defaultModel")
		modelIds.forEach { modelConfig.remove(it) }
		store.delete(id)
		log.info("Deleted provider  id={}  modelCount={}", id, modelIds.count())
	}
	
	override fun create(provider: CoreConfig.ProviderConfig.Provider) {
		require(!store.get().any { it.displayName == provider.displayName })
		val meta = LlmClientLoader.load(provider.type).providerInfo
		store.add(
			ProviderData(
				id = provider.id,
				displayName = provider.displayName,
				providerType = provider.type,
				apiKey = apiKeyConfig.getId(provider.keyId),
				baseUrl = provider.baseUrl ?: meta.baseUrl,
				errorHandlingRules = provider.errorHandlingRules ?: meta.errorHandlingRules,
			)
		)
		log.info("Created provider  id={}  type={}  name={}", provider.id, provider.type, provider.displayName)
	}
	
	override fun updateType(id: UUID, new: String) {
		store.override(get(id).copy(providerType = new))
		log.info("Updated provider type  id={}  type={}", id, new)
	}
	
	override fun updateKey(id: UUID, keyName: String) {
		store.override(get(id).copy(apiKey = apiKeyConfig.getId(keyName)))
		log.info("Updated provider key  id={}  keyName={}", id, keyName)
	}
	
	override fun updateUrl(id: UUID, url: Url) {
		store.override(get(id).copy(baseUrl = url))
		log.info("Updated provider url  id={}  url={}", id, url)
	}
	
	override fun updateRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>) {
		store.override(get(id).copy(errorHandlingRules = rules))
		log.info("Updated provider error rules  id={}  ruleCount={}", id, rules.size)
	}
	
	override fun updateDisplayName(id: UUID, displayName: String) {
		require(!store.get().any { it.displayName == displayName })
		store.override(get(id).copy(displayName = displayName))
		log.info("Updated provider display name  id={}  name={}", id, displayName)
	}
	
	fun get(id: UUID) = store.get(id) ?: error("ProviderData $id not found")
}
