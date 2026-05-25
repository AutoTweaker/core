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

package io.github.autotweaker.core.adapter.config

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.domain.session.ProviderService
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persistence.ProviderStore
import java.util.*

object ProviderConfigAPI {
	private val cfg = ConfigManager
	private val store = ProviderStore
	
	fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
	fun getMeta(type: String) = ProviderService.getInfo(type)
	fun list() = store.get().map {
		CoreConfig.ProviderConfig.Provider(
			id = it.id,
			type = it.providerType,
			keyId = cfg.apiKeyConfig.getName(it.apiKey),
			baseUrl = it.baseUrl,
			displayName = it.displayName,
			errorHandlingRules = it.errorHandlingRules,
		)
	}
	
	fun delete(id: UUID) = store.delete(id)
	
	fun create(provider: CoreConfig.ProviderConfig.Provider) {
		val meta = ProviderService.getInfo(provider.type)
		store.add(
			ProviderData(
				id = provider.id,
				displayName = provider.displayName,
				providerType = provider.type,
				apiKey = cfg.apiKeyConfig.getId(provider.keyId),
				baseUrl = provider.baseUrl ?: meta.baseUrl,
				errorHandlingRules = provider.errorHandlingRules ?: meta.errorHandlingRules,
			)
		)
	}
	
	fun updateType(id: UUID, new: String) = store.override(get(id).copy(providerType = new))
	
	fun updateKey(id: UUID, keyName: String) =
		store.override(get(id).copy(apiKey = cfg.apiKeyConfig.getId(keyName)))
	
	fun updateUrl(id: UUID, url: Url) = store.override(get(id).copy(baseUrl = url))
	
	fun updateRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>) =
		store.override(get(id).copy(errorHandlingRules = rules))
	
	fun updateDisplayName(id: UUID, displayName: String) =
		store.override(get(id).copy(displayName = displayName))
	
	internal fun get(id: UUID) = store.get(id) ?: error("ProviderData $id not found")
}
