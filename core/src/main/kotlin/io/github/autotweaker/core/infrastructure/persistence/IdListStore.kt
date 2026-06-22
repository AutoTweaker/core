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

import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log

class IdListStore<T : Any>(
	kClass: KClass<*>,
	serializer: KSerializer<T>,
	private val idOf: (T) -> UUID,
) : Loggable {
	private val jsonEntry = JsonStoreImpl.namespace(kClass)
	private val className = kClass.qualifiedName
	
	private val listSerializer = ListSerializer(serializer)
	private val items = AtomicReference<List<T>>(emptyList())
	
	init {
		val jsonArray = jsonEntry.get()
		items.set(
			if (jsonArray == null) emptyList()
			else Json.decodeFromJsonElement(listSerializer, jsonArray)
		)
		log.info("Initialized IdListStore  count={}  class={}", items.get().size, className)
	}
	
	@Synchronized
	fun add(data: T) {
		val id = idOf(data)
		if (items.get().any { idOf(it) == id }) error("Already exists  id=$id")
		update(items.get() + data)
		log.debug("Added item  id={}  class={}", id, className)
	}
	
	fun get(): List<T> = items.get()
	
	fun get(id: UUID): T? = items.get().find { idOf(it) == id }
	
	@Synchronized
	fun delete(id: UUID) {
		update(items.get().filterNot { idOf(it) == id })
		log.debug("Deleted item  id={}  class={}", id, className)
	}
	
	@Synchronized
	fun override(data: T) {
		val id = idOf(data)
		update(items.get().map { if (idOf(it) == id) data else it })
		log.debug("Overridden item  id={}  class={}", id, className)
	}
	
	private fun update(new: List<T>) {
		items.set(new)
		jsonEntry.set(Json.encodeToJsonElement(listSerializer, new))
	}
}