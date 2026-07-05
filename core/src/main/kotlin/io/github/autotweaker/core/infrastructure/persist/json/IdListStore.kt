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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import java.util.*

abstract class IdListStore<T : Any> : MutableStore<MutableMap<UUID, T>>(), Loggable {
	protected abstract fun idOf(data: T): UUID
	
	override fun default() = mutableMapOf<UUID, T>()
	
	suspend fun set(data: T) = transform {
		val id = idOf(data)
		it[id] = data
		log.debug("Added item  id={}  class={}", id, this::class.qualifiedName)
	}
	
	suspend fun getAll(): Map<UUID, T> = transform { it.toMap() }
	
	suspend fun get(id: UUID): T? = transform { it[id] }
	
	suspend fun delete(id: UUID): Boolean = transform {
		(it.remove(id) != null).andLog(log) {
			debug("Deleted item  id={}  class={}", id, this@IdListStore::class.qualifiedName)
		}
	}
}
