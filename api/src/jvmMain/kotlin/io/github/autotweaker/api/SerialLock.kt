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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Deprecated(
	"limitedParallelism limits how many threads can execute some code in parallel, but does not limit how many coroutines execute concurrently!",
	level = DeprecationLevel.ERROR
)
class SerialLock(io: Boolean = false) {
	@PublishedApi
	internal val dispatcher: CoroutineDispatcher =
		if (io) Dispatchers.IO.limitedParallelism(1)
		else Dispatchers.Default.limitedParallelism(1)
	
	suspend inline fun <T> withLock(crossinline block: suspend () -> T): T =
		withContext(dispatcher) { block() }
}
