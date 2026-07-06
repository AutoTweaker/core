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

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


/**
 * 快速创建一个协程作用域，并在崩溃时记录日志。
 *
 * 使用 `scope()` 等价于 `CoroutineScope(Dispatchers.Default + SupervisorJob())`。
 *
 * 使用 `scope(IO)` 等价于 `CoroutineScope(Dispatchers.IO + SupervisorJob())`。
 */
fun Loggable.scope(io: IO? = null): CoroutineScope {
	val handler = CoroutineExceptionHandler { _, e -> log.error("Coroutine failed", e) }
	return if (io == null)
		CoroutineScope(Dispatchers.Default + SupervisorJob() + handler)
	else
		CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
}

/**
 * 用于告知 [scope] 使用 IO 线程池 `Dispatchers.IO`。（使用 `scope(IO)`）
 *
 * 只是为了让 [scope] 写起来更好看，不用 `scope(true)` 或者  `scope(io = true)`。
 */
object IO
