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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex

class ReentrantMutex : Traceable {
	@PublishedApi
	internal val mutex = Mutex()
	
	@PublishedApi
	@Volatile
	internal var owner: Job? = null
	
	suspend inline fun <T> withLock(crossinline block: suspend () -> T): T {
		val job = currentCoroutineContext()[Job]
		if (owner === job) return block()
		mutex.lock()
		owner = job
		return trace.catching { block() }
			.also {
				owner = null
				mutex.unlock()
			}
			.getOrThrow()
	}
}
