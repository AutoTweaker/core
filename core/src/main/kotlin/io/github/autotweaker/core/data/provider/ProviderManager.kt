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
		if (providers.any { it.name == data.name }) error("Provider with name ${data.name} already exists")
		update(providers + data)
		logger.debug("Provider added  provider={}  type={}", data.name, data.providerType)
	}
	
	fun addModel(provider: String, models: List<Provider.Model>) {
		update(providers.map {
			if (it.name == provider) {
				val names = it.models.map { model -> model.name }.toSet()
				val duplicates = models.map { model -> model.name }.filter { name -> name in names }.toSet()
				if (duplicates.isNotEmpty()) error("Duplicates model found: $duplicates")
				it.copy(models = it.models + models)
			} else it
		})
		logger.debug("Provider models added  provider={}  count={}", provider, models.size)
	}
	
	fun get(): List<Provider> = providers
	
	fun getInfo(type: String): LlmClient.ProviderInfo = LlmClientLoader.load(type).providerInfo
	
	fun delete(name: String) {
		update(providers.filterNot { it.name == name })
		logger.debug("Provider deleted  provider={}", name)
	}
	
	fun override(data: Provider) {
		update(providers.map { if (it.name == data.name) data else it })
		logger.debug("Provider overridden  provider={}", data.name)
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
			provider = providerData, modelInfo = model.modelInfo, config = model.config, modelId = id
		)
	}
	
	private fun update(new: List<Provider>) {
		providers = new
		jsonEntry.set(Json.encodeToJsonElement(new))
	}
}
