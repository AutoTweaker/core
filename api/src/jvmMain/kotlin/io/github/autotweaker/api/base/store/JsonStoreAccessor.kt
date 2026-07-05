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

package io.github.autotweaker.api.base.store

import io.github.autotweaker.api.storage.JsonStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * internal 所以没有 KDoc。
 */
internal class JsonStoreAccessor<V>(
	private val store: JsonStore,
	private val serializer: KSerializer<V>,
	private val default: () -> V,
) {
	val initial: V by lazy { load() ?: default() }
	
	fun save(value: V) =
		store.set(Json.encodeToJsonElement(serializer, value))
	
	private fun load(): V? =
		store.get()?.let { Json.decodeFromJsonElement(serializer, it) }
}
