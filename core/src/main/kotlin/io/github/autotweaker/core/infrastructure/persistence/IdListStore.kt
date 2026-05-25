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
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

internal class IdListStore<T : Any>(
	kClass: KClass<*>,
	serializer: KSerializer<T>,
	private val idOf: (T) -> UUID,
) {
	private val logger = LoggerFactory.getLogger(IdListStore::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(kClass)
	
	private val listSerializer = ListSerializer(serializer)
	private var items: List<T>
	
	init {
		val jsonArray = jsonEntry.get()
		items = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement(listSerializer, jsonArray)
		logger.info("Initialized  count={}", items.size)
	}
	
	fun add(data: T) {
		val id = idOf(data)
		if (items.any { idOf(it) == id }) error("already exists  id=$id")
		update(items + data)
		logger.debug("Added  id={}", id)
	}
	
	fun get(): List<T> = items
	
	fun get(id: UUID): T? = items.find { idOf(it) == id }
	
	fun delete(id: UUID) {
		update(items.filterNot { idOf(it) == id })
		logger.debug("Deleted  id={}", id)
	}
	
	fun override(data: T) {
		val id = idOf(data)
		update(items.map { if (idOf(it) == id) data else it })
		logger.debug("Overridden  id={}", id)
	}
	
	private fun update(new: List<T>) {
		items = new
		jsonEntry.set(Json.encodeToJsonElement(new))
	}
}