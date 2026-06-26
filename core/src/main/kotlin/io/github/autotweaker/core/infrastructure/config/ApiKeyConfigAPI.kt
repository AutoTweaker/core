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

import io.github.autotweaker.api.JsonStorable
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.store
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.ApiKeyRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ApiKeyConfigAPI : ApiKeyRepository, Loggable, JsonStorable {
	private lateinit var secret: SecretStore
	private val provCfg: ProviderRepository = ProviderConfigAPI
	private val apiKeys = ConcurrentHashMap<String, @Serializable(with = UuidSerializer::class) UUID>()
	
	private val mutex = Mutex()
	
	init {
		store.get()?.let {
			apiKeys.putAll(
				Json.decodeFromJsonElement(MapSerializer(String.serializer(), UuidSerializer), it)
			)
		}
	}
	
	fun init(secretStore: SecretStore) {
		secret = secretStore
	}
	
	override suspend fun add(key: CoreConfig.ProviderConfig.ApiKey) = mutex.withLock {
		require(apiKeys[key.name] == null) { "Key ${key.name} already exists" }
		apiKeys[key.name] = secret.set(key.key)
		save()
		log.info("Added API key  name={}", key.name)
	}
	
	override suspend fun list(): List<String> = mutex.withLock {
		apiKeys.keys.toList()
	}
	
	override suspend fun get(name: String): String = mutex.withLock {
		apiKeys[name]?.let { secret.get(it) } ?: error("Key $name not found")
	}
	
	override suspend fun remove(name: String) = mutex.withLock {
		if (apiKeys[name] == null) return@withLock false
		if (provCfg.list().any { it.keyId == name }) error("Key $name is currently in use")
		val id = apiKeys.remove(name) ?: return@withLock false
		save()
		secret.remove(id)
		log.info("Deleted API key  name={}", name)
		return@withLock true
	}
	
	suspend fun getId(name: String): UUID = mutex.withLock {
		apiKeys[name] ?: error("Key $name not found")
	}
	
	suspend fun getName(id: UUID): String? = mutex.withLock {
		apiKeys.filter { it.value == id }.keys.firstOrNull()
	}
	
	private fun save() =
		store.set(Json.encodeToJsonElement(MapSerializer(String.serializer(), UuidSerializer), apiKeys))
}
