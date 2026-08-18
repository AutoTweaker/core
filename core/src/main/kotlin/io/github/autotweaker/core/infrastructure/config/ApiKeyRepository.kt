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
import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.ApiKeyInUseException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateApiKeyException
import io.github.autotweaker.api.types.exception.notfound.ApiKeyNotFoundException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.serializer
import java.util.*

object ApiKeyRepository : MutableStore<MutableMap<UUID, String>>(), Loggable {
	override val serializer = MutableMapSerializer(
		UuidSerializer,
		String.serializer()
	)
	
	override fun default() = mutableMapOf<UUID, String>()
	
	private lateinit var secret: SecretStore
	
	fun init(secretStore: SecretStore) {
		secret = secretStore
	}
	
	suspend fun add(name: String, key: String) = transform {
		if (it.containsValue(name)) throw DuplicateApiKeyException(name)
		val id = UUID()
		secret.set(key, id)
		it[id] = name
		log.info("Added API key  name={}  length={}", name, key.length)
		return@transform id
	}
	
	suspend fun list() = transform { it.toMap() }
	
	suspend fun checkExists(id: UUID) = transform {
		if (!it.containsKey(id)) throw ApiKeyNotFoundException(id)
	}
	
	suspend fun remove(id: UUID) = ProviderRepository.lock.withLock {
		transform {
			val name = it[id] ?: return@transform false
			if (ProviderRepository.list().any { provider -> provider.apiKey == id })
				throw ApiKeyInUseException(id, name)
			secret.requireUnlocked()
			it.remove(id)
			secret.remove(id)
			log.info("Deleted API key  name={}", name)
			return@transform true
		}
	}
}
