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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ApiKeyConfigAPI : ApiKeyRepository, Loggable, JsonStorable {
	private lateinit var secret: SecretStore
	private val provCfg: ProviderRepository = ProviderConfigAPI
	private val keyMap = ConcurrentHashMap<String, @Serializable(with = UuidSerializer::class) UUID>()
	
	fun init(secretStore: SecretStore) {
		secret = secretStore
	}
	
	
	override suspend fun add(key: CoreConfig.ProviderConfig.ApiKey) {
		if (keyMap[key.name] != null) error("Key ${key.name} already exists")
		keyMap[key.name] = secret.add(key.key)
		saveMap()
		log.info("Added API key  name={}", key.name)
	}
	
	override fun list(): List<String> = keyMap.keys.toList()
	override suspend fun get(name: String): String =
		keyMap[name]?.let { secret.get(it) } ?: error("Key $name not found")
	
	override suspend fun delete(name: String) {
		if (provCfg.list().any { it.keyId == name }) error("Key $name is currently in use")
		val id = keyMap.remove(name) ?: error("Key $name not found")
		secret.remove(id)
		saveMap()
		log.info("Deleted API key  name={}", name)
	}
	
	fun getId(name: String): UUID = keyMap[name] ?: error("Key $name not found")
	fun getName(id: UUID): String =
		keyMap.filter { it.value == id }.keys.firstOrNull() ?: error("Key $id not found")
	
	init {
		store.get()?.let {
			keyMap.putAll(
				Json.decodeFromJsonElement(MapSerializer(String.serializer(), UuidSerializer), it)
			)
		}
	}
	
	private fun saveMap() =
		store.set(Json.encodeToJsonElement(MapSerializer(String.serializer(), UuidSerializer), keyMap))
}
