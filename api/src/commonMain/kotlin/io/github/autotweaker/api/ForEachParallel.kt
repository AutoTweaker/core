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

@file:Suppress("unused")

package io.github.autotweaker.api

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <T> Iterable<T>.forEachParallel(
	crossinline action: suspend (T) -> Unit
) = coroutineScope {
	forEach {
		launch {
			action(it)
		}
	}
}

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]，同时限制并发数为 [limit]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <T> Iterable<T>.forEachParallel(
	limit: Int, crossinline action: suspend (T) -> Unit
) {
	val semaphore = Semaphore(limit)
	forEachParallel {
		semaphore.withPermit {
			action(it)
		}
	}
}

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <K, V> Map<K, V>.forEachParallel(
	crossinline action: suspend (Map.Entry<K, V>) -> Unit
) = entries.forEachParallel(action)

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]，同时限制并发数为 [limit]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <K, V> Map<K, V>.forEachParallel(
	limit: Int, crossinline action: suspend (Map.Entry<K, V>) -> Unit
) = entries.forEachParallel(limit, action)

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <T> Array<T>.forEachParallel(
	crossinline action: suspend (T) -> Unit
) = asIterable().forEachParallel(action)

/**
 * [forEach] 并行版，会为每个条目启动一个协程来执行 [action]，同时限制并发数为 [limit]。
 *
 * 用法与 [forEach] 近似，但 lambda 内部不允许非局部返回，因为 lambda 要用来启动协程。
 */
suspend inline fun <T> Array<T>.forEachParallel(
	limit: Int, crossinline action: suspend (T) -> Unit
) = asIterable().forEachParallel(limit, action)
