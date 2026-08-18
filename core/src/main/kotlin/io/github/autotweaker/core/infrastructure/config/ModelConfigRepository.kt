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
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.DefaultModelDeletionException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateModelNameException
import io.github.autotweaker.api.types.exception.notfound.ProviderNotFoundException
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.json.ModelStore
import java.util.*

object ModelConfigRepository : Loggable {
	private val store = ModelStore
	private val lock = ReentrantMutex()
	
	suspend fun set(model: ModelData) = lock.withLock {
		ProviderRepository.lock.withLock {
			ProviderRepository.get(model.providerId)
				?: throw ProviderNotFoundException(model.providerId)
			val duplicate = store.getAll().values.any {
				it.id != model.id
						&& it.providerId == model.providerId
						&& it.displayName == model.displayName
			}
			if (duplicate) throw DuplicateModelNameException(model.displayName)
			store.set(model)
			log.info("Added model  id={}  modelId={}", model.id, model.modelInfo.modelId)
		}
	}
	
	suspend fun list(): List<ModelData> = store.getAll().values.toList()
	
	suspend fun get(id: UUID) = store.get(id)
	
	suspend fun remove(id: UUID): Boolean =
		ModelResolverImpl.getDefaultModel {
			if (it == id) throw DefaultModelDeletionException(it)
			store.delete(id).andLog(log) {
				info("Removed model  id={}", id)
			}
		}
}
