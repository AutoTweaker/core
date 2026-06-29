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
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.log
import io.github.autotweaker.core.domain.port.ModelConfigRepository
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.json.ModelStore
import java.util.*
import io.github.autotweaker.api.types.config.CoreConfig.ProviderConfig.Model as ModelConfig

object ModelConfigAPI : ModelConfigRepository, Loggable {
	private val store = ModelStore
	
	override suspend fun set(model: ModelConfig) {
		require(store.getAll().values.all {
			it.displayName != model.data.displayName
					&& it.providerId == model.data.providerId
		})
		store.set(model.data)
		log.info("Added model  id={}  modelId={}", model.data.id, model.data.modelInfo.modelId)
	}
	
	override suspend fun list() = store.getAll().values.map { ModelConfig(it) }
	
	override suspend fun get(id: UUID) = store.get(id)?.let { ModelConfig(it) }
	
	override suspend fun remove(id: UUID): Boolean {
		check(ModelResolverImpl.getDefaultModel() != id) { "Cannot remove default model: $id" }
		return store.delete(id).andLog(log) { info("Removed model  id={}", id) }
	}
}
