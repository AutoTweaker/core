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

package io.github.autotweaker.core.infrastructure.persistence

import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

class EnvStorage(private val kClass: KClass<*>, private val secretStore: SecretStore) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(kClass)
	private val mutex = Mutex()

	suspend fun listEnv(): List<String> = mutex.withLock { getEnvUuidMap().keys.toList() }

	suspend fun getEnv(id: String): String? = mutex.withLock {
		val uuid = getEnvUuidMap()[id] ?: return@withLock null
		try {
			secretStore.get(uuid)
		} catch (_: Exception) {
			null
		}
	}

	suspend fun setEnv(id: String, value: String) = mutex.withLock {
		val current = getEnvUuidMap()
		current[id]?.let { secretStore.remove(it) }
		val uuid = secretStore.add(value)
		val updated =
			JsonObject(current.mapValues { (_, v) -> JsonPrimitive(v.toString()) } + (id to JsonPrimitive(uuid.toString())))
		jsonEntry.set(updated)
		logger.debug("Env set  id={} class={}", id, kClass.java.name)
	}

	suspend fun removeEnv(id: String) = mutex.withLock {
		val current = getEnvUuidMap()
		current[id]?.let { secretStore.remove(it) }
		val updated = JsonObject(current.filterKeys { it != id }.mapValues { (_, v) -> JsonPrimitive(v.toString()) })
		jsonEntry.set(updated)
		logger.debug("Env removed  id={} class={}", id, kClass.java.name)
	}

	private fun getEnvUuidMap(): Map<String, UUID> {
		val obj = jsonEntry.get() as? JsonObject ?: return emptyMap()
		return obj.mapNotNull { (k, v) ->
			v.jsonPrimitive.contentOrNull?.let { UUID.fromString(it) }?.let { k to it }
		}.toMap()
	}
}
