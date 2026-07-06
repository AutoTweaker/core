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

package io.github.autotweaker.api.base

import io.github.autotweaker.api.trace.TraceRecorder

/**
 * [runCatching] 的封装，[onFailure] 自动记录异常到 [TraceRecorder]，需要调用方实现 [io.github.autotweaker.api.Traceable]。
 *
 * [block] 虽然看起来不是 suspend，但 [catching] 是 inline fun，只要调用点有 suspend 上下文，[block] 就是 suspend 的。
 *
 * [CatchingResult] 的错误处理 API 已经足够完善，可以使用 `trace.catching` 来代替 [runCatching] 以及 `try-catch-finally`，`try-finally` 也可以由 `CatchingResult.also.getOrThrow` 替代。
 */
inline fun <T> TraceRecorder.catching(block: () -> T): CatchingResult<T> =
	CatchingResult(runCatching { block() }.onFailure { exception(it) })

/**
 * [runCatching] 的封装，[onFailure] 自动记录异常到 [TraceRecorder]，需要调用方实现 [io.github.autotweaker.api.Traceable]。
 *
 * [block] 虽然看起来不是 suspend，但 [catching] 是 inline fun，只要调用点有 suspend 上下文，[block] 就是 suspend 的。
 *
 * [CatchingResult] 的错误处理 API 已经足够完善，可以使用 `trace.catching` 来代替 [runCatching] 以及 `try-catch-finally`，`try-finally` 也可以由 `CatchingResult.also.getOrThrow` 替代。
 */
inline fun <T, R> T.catching(recorder: TraceRecorder, block: T.() -> R): CatchingResult<R> =
	CatchingResult(runCatching { block() }.onFailure { recorder.exception(it) })
