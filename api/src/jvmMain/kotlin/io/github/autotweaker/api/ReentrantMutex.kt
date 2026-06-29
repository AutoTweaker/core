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

/**
 * 基于 [Mutex] 的可重入锁，相同协程以及子协程都允许重入。
 *
 * 实际上只要上下文相同，就可重入，一般情况下子协程继承当前协程上下文，外部协程也不可能继承当前协程上下文。
 *
 * 如果手动修改子协程上下文，或使用当前协程上下文启动外部协程，重入行为可能发生改变。
 */
class ReentrantMutex : Traceable {
	@PublishedApi
	internal val mutex = Mutex()
	
	@PublishedApi
	internal val lockKey = object : CoroutineContext.Key<CoroutineContext.Element> {}
	
	@PublishedApi
	internal val key = object : CoroutineContext.Element {
		override val key get() = lockKey
	}
	
	/**
	 * 请注意，[block] 内不可使用非局部返回（如 `return`）。
	 *
	 * @throws Throwable 当 [block] 抛出异常。
	 */
	suspend inline fun <T> withLock(crossinline block: suspend () -> T): T {
		if (currentCoroutineContext()[lockKey] === key) return block()
		mutex.lock()
		return trace.catching { withContext(key) { block() } }
			.also { mutex.unlock() }
			.getOrThrow()
	}
}
