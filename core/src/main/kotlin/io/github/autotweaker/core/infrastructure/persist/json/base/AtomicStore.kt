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

package io.github.autotweaker.core.infrastructure.persist.json.base

import io.github.autotweaker.api.IO
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.scope
import io.github.autotweaker.api.store
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

abstract class AtomicStore<V> : StoreBase<V>(), Loggable {
	@Volatile
	private var initialized = false
	private val accessor by lazy { JsonStoreAccessor(store, serializer, ::default).also { initialized = true } }
	private val cache: AtomicReference<V> by lazy { AtomicReference(accessor.initial) }
	private val dirty = AtomicBoolean(false)
	private val scope = scope(IO)
	
	init {
		scope.launch {
			while (true) {
				delay(500.milliseconds)
				if (dirty.compareAndSet(true, false)) {
					accessor.save(cache.get())
				}
			}
		}
	}
	
	fun shutdown() {
		scope.cancel()
		if (initialized && dirty.get()) accessor.save(cache.get())
	}
	
	protected fun get(): V = cache.get()
	
	protected fun set(value: V) {
		cache.set(value)
		dirty.set(true)
	}
	
	protected fun update(transform: (V) -> V) {
		cache.updateAndGet(transform)
		dirty.set(true)
	}
}
