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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.reflect.KClass

class EnvStorage(
	private val kClass: KClass<*>, private val store: JsonStore, private val secretStore: SecretStore
) : Loggable, Traceable {
	private val serializer = MapSerializer(
		String.serializer(), UuidSerializer
	)
	private val envs: MutableMap<String, UUID> by lazy {
		store.get()?.let {
			Json.decodeFromJsonElement(serializer, it)
		}.orEmpty().toMutableMap()
	}
	private val lock = ReentrantMutex()
	
	suspend fun listEnv(): List<String> = lock.withLock { envs.keys.toList() }
	
	suspend fun getEnv(id: String): String? = lock.withLock {
		val uuid = envs[id] ?: return@withLock null
		trace.catching { secretStore.get(uuid) }
			.rethrow<SecretStoreLockedException>()
			.onFailure { log.warn("Failed env retrieval  id={}  class={}", id, kClass.java.name) }
			.getOrNull()
	}
	
	suspend fun setEnv(id: String, value: String) = lock.withLock {
		envs[id]?.let { secretStore.remove(it) }
		envs[id] = secretStore.set(value)
		andSave().andLog(log) { debug("Set env  id={}  class={}", id, kClass.java.name) }
	}
	
	suspend fun removeEnv(id: String): Boolean = lock.withLock {
		envs[id]?.let { secretStore.remove(it) }
		return@withLock envs.remove(id).andLog(log) {
			debug("Removed env  id={}  class={}", id, kClass.java.name)
		} != null
	}
	
	private fun <T> T.andSave() = also {
		store.set(
			Json.encodeToJsonElement(
				serializer,
				envs
			)
		)
	}
}
