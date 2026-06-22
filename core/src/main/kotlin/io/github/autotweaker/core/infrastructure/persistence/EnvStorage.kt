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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.trace
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import kotlin.reflect.KClass

class EnvStorage(private val kClass: KClass<*>, private val store: JsonStore, private val secretStore: SecretStore) :
	Loggable, Traceable {
	private val mutex = Mutex()
	
	suspend fun listEnv(): List<String> = mutex.withLock { getEnvUuidMap().keys.toList() }
	
	suspend fun getEnv(id: String): String? = mutex.withLock {
		val uuid = getEnvUuidMap()[id] ?: return@withLock null
		trace.catching { secretStore.get(uuid) }
			.onFailure { log.warn("Failed env retrieval  id={}  class={}", id, kClass.java.name) }
			.getOrNull()
	}
	
	suspend fun setEnv(id: String, value: String) = mutex.withLock {
		val current = getEnvUuidMap()
		current[id]?.let { secretStore.remove(it) }
		val uuid = secretStore.add(value)
		val updated =
			JsonObject(current.mapValues { (_, v) -> JsonPrimitive(v.toString()) } + (id to JsonPrimitive(uuid.toString())))
		store.set(updated)
		log.debug("Set env  id={}  class={}", id, kClass.java.name)
	}
	
	suspend fun removeEnv(id: String) = mutex.withLock {
		val current = getEnvUuidMap()
		current[id]?.let { secretStore.remove(it) }
		val updated = JsonObject(current.filterKeys { it != id }.mapValues { (_, v) -> JsonPrimitive(v.toString()) })
		store.set(updated)
		log.debug("Removed env  id={}  class={}", id, kClass.java.name)
	}
	
	private fun getEnvUuidMap(): Map<String, UUID> {
		val obj = store.get() as? JsonObject ?: return emptyMap()
		return obj.mapNotNull { (k, v) ->
			v.jsonPrimitive.contentOrNull?.let { UUID.fromString(it) }?.let { k to it }
		}.toMap()
	}
}
