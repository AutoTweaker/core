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

package io.github.autotweaker.core.data

import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.core.data.json.JsonStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory

object ProviderStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStore.namespace(this::class.java.name)
	
	private var providers: List<ProviderData>
	
	init {
		val jsonArray = jsonEntry.get()
		providers = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement<List<ProviderData>>(jsonArray)
		logger.info("ProviderStore initialized  providerCount={}", providers.size)
	}
	
	fun add(data: ProviderData) {
		if (providers.any { it.name == data.name }) error("ProviderData with name ${data.name} already exists")
		update(providers + data)
		logger.debug("ProviderData added  provider={}  type={}", data.name, data.providerType)
	}
	
	fun addModel(provider: String, models: List<ProviderData.ModelData>) {
		update(providers.map {
			if (it.name == provider) {
				val names = it.models.map { model -> model.name }.toSet()
				val duplicates = models.map { model -> model.name }.filter { name -> name in names }.toSet()
				if (duplicates.isNotEmpty()) error("Duplicates model found: $duplicates")
				it.copy(models = it.models + models)
			} else it
		})
		logger.debug("ProviderData models added  provider={}  count={}", provider, models.size)
	}
	
	fun get(): List<ProviderData> = providers
	
	fun delete(name: String) {
		update(providers.filterNot { it.name == name })
		logger.debug("ProviderData deleted  provider={}", name)
	}
	
	fun override(data: ProviderData) {
		update(providers.map { if (it.name == data.name) data else it })
		logger.debug("ProviderData overridden  provider={}", data.name)
	}
	
	private fun update(new: List<ProviderData>) {
		providers = new
		jsonEntry.set(Json.encodeToJsonElement(new))
	}
}