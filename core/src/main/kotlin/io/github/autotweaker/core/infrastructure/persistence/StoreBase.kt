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
import kotlinx.serialization.KSerializer

abstract class StoreBase<V>(
	serializer: KSerializer<V>,
) : JsonStorable {
	private val accessor = JsonStoreAccessor(store, serializer)
	
	protected fun initial(): V = accessor.load() ?: default()
	
	protected abstract fun default(): V
	
	protected fun save(value: V) = accessor.save(value)
}
