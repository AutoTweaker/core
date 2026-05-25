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

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persistence.ProviderStore
import java.util.*

object ProviderConfigAPI : ProviderRepository {
	private val apiKeyConfig = ApiKeyConfigAPI
	private val store = ProviderStore
	
	override fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
	override fun getMeta(type: String) = LlmClientLoader.load(type).providerInfo
	override fun list() = store.get().map {
		CoreConfig.ProviderConfig.Provider(
			id = it.id,
			type = it.providerType,
			keyId = apiKeyConfig.getName(it.apiKey),
			baseUrl = it.baseUrl,
			displayName = it.displayName,
			errorHandlingRules = it.errorHandlingRules,
		)
	}
	
	override fun delete(id: UUID) = store.delete(id)
	
	override fun create(provider: CoreConfig.ProviderConfig.Provider) {
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
	}
	
	override fun updateType(id: UUID, new: String) = store.override(get(id).copy(providerType = new))
	
	override fun updateKey(id: UUID, keyName: String) =
		store.override(get(id).copy(apiKey = apiKeyConfig.getId(keyName)))
	
	override fun updateUrl(id: UUID, url: Url) = store.override(get(id).copy(baseUrl = url))
	
	override fun updateRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>) =
		store.override(get(id).copy(errorHandlingRules = rules))
	
	override fun updateDisplayName(id: UUID, displayName: String) =
		store.override(get(id).copy(displayName = displayName))
	
	internal fun get(id: UUID) = store.get(id) ?: error("ProviderData $id not found")
}
