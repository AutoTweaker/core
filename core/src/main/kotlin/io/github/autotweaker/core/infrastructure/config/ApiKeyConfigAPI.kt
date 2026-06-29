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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.ApiKeyRepository
import io.github.autotweaker.core.domain.port.ProviderRepository
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.*


object ApiKeyConfigAPI : ApiKeyRepository, Loggable, JsonStorable {
	private lateinit var secret: SecretStore
	private val provCfg: ProviderRepository = ProviderConfigAPI
	private val serializer = MapSerializer(String.serializer(), UuidSerializer)
	private val apiKeys: MutableMap<String, UUID> by lazy {
		store.get()?.let {
			Json.decodeFromJsonElement(serializer, it)
		}.orEmpty().toMutableMap()
	}
	
	private val lock = ReentrantMutex()
	
	fun init(secretStore: SecretStore) {
		secret = secretStore
	}
	
	override suspend fun add(key: CoreConfig.ProviderConfig.ApiKey) = lock.withLock {
		require(apiKeys[key.name] == null) { "Key ${key.name} already exists" }
		apiKeys[key.name] = secret.set(key.key)
		save()
		log.info("Added API key  name={}", key.name)
	}
	
	override suspend fun list(): List<String> = lock.withLock {
		apiKeys.keys.toList()
	}
	
	override suspend fun get(name: String): String = lock.withLock {
		apiKeys[name]?.let { secret.get(it) } ?: error("Key $name not found")
	}
	
	override suspend fun remove(name: String) = lock.withLock {
		if (apiKeys[name] == null) return@withLock false
		if (provCfg.list().any { it.keyId == name }) error("Key $name is currently in use")
		val id = apiKeys.remove(name) ?: return@withLock false
		save()
		secret.remove(id)
		log.info("Deleted API key  name={}", name)
		return@withLock true
	}
	
	suspend fun getId(name: String): UUID = lock.withLock {
		apiKeys[name] ?: error("Key $name not found")
	}
	
	suspend fun getName(id: UUID): String? = lock.withLock {
		apiKeys.filter { it.value == id }.keys.firstOrNull()
	}
	
	private fun save() = store.set(Json.encodeToJsonElement(serializer, apiKeys))
}
