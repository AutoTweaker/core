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

package io.github.autotweaker.core.data.provider

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.llm.LlmClient
import io.github.autotweaker.core.llm.LlmClientLoader
import io.github.autotweaker.core.secret.SecretManager
import io.github.autotweaker.core.session.ModelId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

object ProviderManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStore.namespace(this::class.java.name)
	
	private var providers: List<Provider>
	
	init {
		val jsonArray = jsonEntry.get()
		providers = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement<List<Provider>>(jsonArray)
		logger.info("ProviderManager initialized  providerCount={}", providers.size)
	}
	
	fun add(data: Provider) {
		logger.debug("Provider added  name={}  type={}", data.name, data.providerType)
		update(providers + data)
	}
	
	fun addModel(provider: String, models: List<Provider.Model>) {
		logger.debug("Provider models added  provider={}  count={}", provider, models.size)
		update(providers.map { if (it.name == provider) it.copy(models = it.models + models) else it })
	}
	
	fun get(): List<Provider> =
		providers
	
	fun getInfo(type: String): LlmClient.ProviderInfo =
		LlmClientLoader.load(type).providerInfo
	
	fun delete(name: String) {
		logger.debug("Provider deleted  name={}", name)
		update(providers.filterNot { it.name == name })
	}
	
	fun override(data: Provider) {
		logger.debug("Provider overridden  name={}", data.name)
		update(providers.map { if (it.name == data.name) data else it })
	}
	
	fun getModel(id: ModelId): Model? {
		val provider = providers.find { it.name == id.provider } ?: return null
		val model = provider.models.find { it.name == id.modelName } ?: return null
		val providerData = io.github.autotweaker.core.agent.llm.Provider(
			name = provider.providerType,
			baseUrl = provider.baseUrl,
			apiKey = SecretManager.get(provider.apiKey),
			errorHandlingRules = provider.errorHandlingRules
		)
		return Model(
			provider = providerData,
			modelInfo = model.modelInfo,
			config = model.config,
			modelId = id
		)
	}
	
	private fun update(new: List<Provider>) {
		providers = new
		jsonEntry.set(Json.encodeToJsonElement(new))
	}
}
