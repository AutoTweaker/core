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

package io.github.autotweaker.api

import io.github.autotweaker.api.trace.catching
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ReentrantMutex : Traceable {
	@PublishedApi
	internal val mutex = Mutex()
	
	@PublishedApi
	internal val lockKey = object : CoroutineContext.Key<CoroutineContext.Element> {}
	
	@PublishedApi
	internal val key = object : CoroutineContext.Element {
		override val key get() = lockKey
	}
	
	suspend inline fun <T> withLock(crossinline block: suspend () -> T): T {
		if (currentCoroutineContext()[lockKey] === key) return block()
		mutex.lock()
		return trace.catching { withContext(key) { block() } }
			.also { mutex.unlock() }
			.getOrThrow()
	}
}
