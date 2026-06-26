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

import io.github.autotweaker.api.JsonStorable
import io.github.autotweaker.api.store
import io.github.autotweaker.api.types.llm.ModelData
import java.util.*

object ModelStore : JsonStorable {
	private val listStore =
		IdListStore(this::class, store, ModelData.serializer()) { it.id }
	
	suspend fun set(data: ModelData) = listStore.set(data)
	suspend fun get(id: UUID) = listStore.get(id)
	suspend fun getAll() = listStore.getAll()
	suspend fun delete(id: UUID) = listStore.delete(id)
}
