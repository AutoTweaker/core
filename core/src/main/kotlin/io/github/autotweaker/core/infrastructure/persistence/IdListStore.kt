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
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.reflect.KClass

class IdListStore<T : Any>(
	kClass: KClass<*>,
	private val store: JsonStore,
	serializer: KSerializer<T>,
	private val idOf: (T) -> UUID,
) : Loggable {
	private val className = kClass.qualifiedName
	
	private val mapSerializer = MapSerializer(UuidSerializer, serializer)
	private val items = mutableMapOf<UUID, T>()
	
	private val mutex = Mutex()
	
	init {
		store.get()?.let {
			items.putAll(Json.decodeFromJsonElement(mapSerializer, it))
		}
		log.info("Initialized IdListStore  count={}  class={}", items.size, className)
	}
	
	suspend fun set(data: T) = mutex.withLock {
		val id = idOf(data)
		items[id] = data
		andSave()
		log.debug("Added item  id={}  class={}", id, className)
	}
	
	suspend fun getAll(): Map<UUID, T> = mutex.withLock { items.toMap() }
	
	suspend fun get(id: UUID): T? = mutex.withLock { items[id] }
	
	suspend fun delete(id: UUID): Boolean = mutex.withLock {
		(items.remove(id) != null).andSave().andLog(log)
		{ debug("Deleted item  id={}  class={}", id, className) }
	}
	
	private fun <T> T.andSave() = also {
		store.set(
			Json.encodeToJsonElement(
				mapSerializer,
				items
			)
		)
	}
}
