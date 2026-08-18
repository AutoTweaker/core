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
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.DefaultModelDeletionException
import io.github.autotweaker.api.types.exception.UnknownProviderTypeException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateProviderNameException
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.infrastructure.llm.LlmClientLoader
import io.github.autotweaker.core.infrastructure.persist.json.ModelResolverImpl
import io.github.autotweaker.core.infrastructure.persist.json.ProviderStore
import java.util.*

object ProviderRepository : Loggable, Traceable {
	private val store = ProviderStore
	
	val lock = ReentrantMutex()
	
	fun listAvailable(): Set<String> = LlmClientLoader.available()
	fun getMeta(type: String) = LlmClientLoader.load(type).providerInfo
	
	suspend fun list() = store.getAll().values.toList()
	
	suspend fun get(id: UUID): ProviderData? = store.get(id)
	
	suspend fun remove(id: UUID): Boolean = lock.withLock {
		val modelIds = ModelConfigRepository.list().filter { it.providerId == id }.map { it.id }
		ModelResolverImpl.getDefaultModel {
			it?.let { defaultModel ->
				if (defaultModel in modelIds) throw DefaultModelDeletionException(defaultModel, id)
			}
			modelIds.forEach { model -> ModelConfigRepository.remove(model) }
		}
		return@withLock store.delete(id).andLog(log) {
			info("Deleted provider  id={}  modelCount={}", id, modelIds.count())
		}
	}
	
	suspend fun set(provider: ProviderData) = lock.withLock {
		if (store.getAll().values.any { it.id != provider.id && it.displayName == provider.displayName })
			throw DuplicateProviderNameException(provider.displayName)
		if (provider.providerType !in LlmClientLoader.available()) throw UnknownProviderTypeException(provider.providerType)
		ApiKeyRepository.checkExists(provider.apiKey)
		store.set(provider).andLog(log) {
			info("Created provider  id={}  type={}  name={}", provider.id, provider.providerType, provider.displayName)
		}
	}
}
