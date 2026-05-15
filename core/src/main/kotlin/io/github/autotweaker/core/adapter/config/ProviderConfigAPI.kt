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
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.core.data.ProviderStore
import io.github.autotweaker.core.llm.LlmClientLoader
import io.github.autotweaker.core.session.ProviderService

object ProviderConfigAPI {
	private val cfg = ConfigManager
	private val store = ProviderStore
	
	fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
	fun getMeta(type: String) = ProviderService.getInfo(type)
	fun list() = store.get().map {
		CoreConfig.ProviderConfig.Provider(
			name = it.name,
			type = it.providerType,
			keyId = cfg.apiKeyConfig.getName(it.apiKey),
			baseUrl = it.baseUrl,
			errorHandlingRules = it.errorHandlingRules,
		)
	}
	
	fun delete(name: String) = store.delete(name)
	
	fun create(provider: CoreConfig.ProviderConfig.Provider) {
		val meta = ProviderService.getInfo(provider.type)
		store.add(
			ProviderData(
				name = provider.name,
				providerType = provider.type,
				apiKey = cfg.apiKeyConfig.getId(provider.keyId),
				baseUrl = provider.baseUrl ?: meta.baseUrl,
				models = emptyList(),
				errorHandlingRules = provider.errorHandlingRules ?: meta.errorHandlingRules,
			)
		)
	}
	
	fun updateType(name: String, new: String) = store.override(get(name).copy(providerType = new))
	
	fun updateKey(name: String, keyName: String) =
		store.override(get(name).copy(apiKey = cfg.apiKeyConfig.getId(keyName)))
	
	fun updateUrl(name: String, url: Url) = store.override(get(name).copy(baseUrl = url))
	
	fun updateRule(name: String, rules: List<ProviderData.ErrorHandlingRule>) =
		store.override(get(name).copy(errorHandlingRules = rules))
	
	fun rename(name: String, new: String) {
		val old = get(name)
		store.delete(name)
		store.add(old.copy(name = new))
	}
	
	internal fun get(name: String) = store.get().find { it.name == name } ?: error("ProviderData $name not found")
}