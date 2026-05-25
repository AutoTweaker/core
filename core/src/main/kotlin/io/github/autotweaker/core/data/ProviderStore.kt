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

package io.github.autotweaker.core.data

import io.github.autotweaker.api.types.llm.ProviderData
import java.util.*

object ProviderStore {
	private val store = IdListStore(this::class, ProviderData.serializer()) { it.id }
	
	fun add(data: ProviderData) = store.add(data)
	fun get(): List<ProviderData> = store.get()
	fun get(id: UUID): ProviderData? = store.get(id)
	fun delete(id: UUID) = store.delete(id)
	fun override(data: ProviderData) = store.override(data)
}

