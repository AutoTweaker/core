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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.exception.*
import io.github.autotweaker.api.types.exception.duplicate.*
import io.github.autotweaker.api.types.exception.notfound.*
import io.github.autotweaker.core.domain.port.ApiKeyRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.infrastructure.persist.json.base.SecretMapStore
import java.util.*

object ApiKeyConfigAPI : SecretMapStore(), ApiKeyRepository, Loggable {
	private val provCfg: ProviderRepository = ProviderConfigAPI
	
	override suspend fun add(key: CoreConfig.ProviderConfig.ApiKey) = transform {
		if (it.containsKey(key.name)) throw DuplicateApiKeyException(key.name)
		putSecret(key.name, key.key)
		log.info("Added API key  name={}", key.name)
	}
	
	override suspend fun list() = listSecrets()
	
	override suspend fun get(name: String) = getSecret(name)
		?: throw ApiKeyNotFoundException(name)
	
	override suspend fun remove(name: String) = ProviderConfigAPI.lock.withLock {
		transform {
			if (!it.containsKey(name)) return@transform false
			if (provCfg.list().any { provider -> provider.keyId == name })
				throw ApiKeyInUseException(name)
			removeSecret(name)
			log.info("Deleted API key  name={}", name)
			return@transform true
		}
	}
	
	suspend fun getId(name: String): UUID = transform {
		it[name] ?: throw ApiKeyNotFoundException(name)
	}
	
	suspend fun getName(id: UUID): String? = transform { map ->
		map.entries.find { it.value == id }?.key
	}
}
