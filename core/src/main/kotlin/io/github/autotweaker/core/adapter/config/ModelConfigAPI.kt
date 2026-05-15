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

import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.model.ModelId
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.core.data.ProviderStore

object ModelConfigAPI {
	private val cfg = ConfigManager
	private val store = ProviderStore
	
	fun getMeta(provider: String, modelId: String) =
		cfg.providerConfig.getMeta(provider).models.find { it.id == modelId }
	
	fun add(model: CoreConfig.ProviderConfig.Model) {
		val modelInfo = model.meta ?: getMeta(model.providerName, model.name) ?: error("Default meta not found")
		val newModel = ProviderData.ModelData(
			name = model.name,
			modelInfo = modelInfo,
			config = model.config,
		)
		store.addModel(model.providerName, listOf(newModel))
	}
	
	fun list() = store.get().flatMap {
		it.models.map { model ->
			CoreConfig.ProviderConfig.Model(
				name = model.name,
				providerName = it.name,
				meta = model.modelInfo,
				config = model.config,
			)
		}
	}
	
	fun listId() = list().map { ModelId(provider = it.providerName, modelName = it.name) }
	
	fun remove(id: ModelId) {
		val provider = cfg.providerConfig.get(id.provider)
		val updatedModels = provider.models.filterNot { it.name == id.modelName }
		store.override(provider.copy(models = updatedModels))
	}
	
	fun update(id: ModelId, model: CoreConfig.ProviderConfig.Model) {
		val provider = cfg.providerConfig.get(id.provider)
		val modelInfo = model.meta ?: getMeta(model.providerName, model.name) ?: error("Default meta not found")
		val newModel = ProviderData.ModelData(
			name = model.name,
			modelInfo = modelInfo,
			config = model.config,
		)
		val updatedModels = provider.models.map {
			if (it.name == id.modelName) newModel else it
		}
		store.override(provider.copy(models = updatedModels))
	}
}