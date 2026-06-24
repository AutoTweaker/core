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

package io.github.autotweaker.api.trace

import kotlinx.coroutines.CancellationException

/**
 * [Result] 的包装，泛型 [T] 在类级别，提供成员函数来弥补 [Result] 错误处理方面的缺陷，并转发 [Result] 的标准 API。
 * 为了避免 `<E, T>` 导致编译器无法推断泛型类型，包装 [Result]，成员函数就不需要 [T] 了。
 */
@JvmInline
value class CatchingResult<T> @PublishedApi internal constructor(@PublishedApi internal val result: Result<T>) {
	/**
	 * 重新抛出类型为 [E] 的异常，请使用 [rethrowCancellation] 替代 `rethrow<CancellationException>()`
	 */
	inline fun <reified E : Throwable> rethrow() =
		onException<E> { throw it }
	
	/**
	 * 重新抛出类型为 [CancellationException] 的异常
	 */
	fun rethrowCancellation() =
		rethrow<CancellationException>()
	
	/**
	 * 如果异常为 [E]，执行 [on] 并抛出异常。请使用 [rethrowCancellation] 替代 `rethrow<CancellationException>()`
	 */
	inline fun <reified E : Throwable> rethrow(on: (E) -> Unit) =
		onException<E> { on(it); throw it }
	
	/**
	 * 如果异常为 [CancellationException]，执行 [on] 并抛出 [CancellationException]
	 */
	fun rethrowCancellation(on: (CancellationException) -> Unit) =
		rethrow<CancellationException>(on)
	
	/**
	 * 如果异常不是 [E]，重新抛出
	 */
	inline fun <reified E : Throwable> rethrowNot() = rethrowIf { it !is E }
	
	/**
	 * 如果异常为 [E]，执行 [action]
	 */
	inline fun <reified E : Throwable> onException(action: (E) -> Unit) =
		onFailure { if (it is E) action(it) }
	
	/**
	 * 如果异常不是 [E]，执行 [action]
	 */
	inline fun <reified E : Throwable> onExceptionExcept(action: (Throwable) -> Unit) =
		onFailureIf({ it !is E }, action)
	
	/**
	 * 如果 [test]，重新抛出异常
	 */
	inline fun rethrowIf(test: (Throwable) -> Boolean) =
		onFailureIf(test) { throw it }
	
	/**
	 * 如果 [test]，执行 [on] 并重新抛出异常
	 */
	inline fun rethrowIf(test: (Throwable) -> Boolean, on: (Throwable) -> Unit) =
		onFailureIf(test) { on(it); throw it }
	
	/**
	 * 如果 [test]，执行 [action]
	 */
	inline fun onFailureIf(test: (Throwable) -> Boolean, action: (Throwable) -> Unit) =
		onFailure { if (test(it)) action(it) }
	
	/**
	 * 如果 [test]，对 [T] 进行 [transform]，返回新的 [CatchingResult]
	 */
	inline fun <R, T : R> CatchingResult<T>.recoverIf(
		test: (Throwable) -> Boolean, transform: (Throwable) -> R
	) = recover { if (test(it)) transform(it) else it }
	
	
	//region Result 的 API 转发
	
	val isSuccess: Boolean get() = result.isSuccess
	val isFailure: Boolean get() = result.isFailure
	
	fun exceptionOrNull(): Throwable? = result.exceptionOrNull()
	
	inline fun <R> fold(
		onSuccess: (T) -> R,
		onFailure: (Throwable) -> R,
	): R = result.fold(onSuccess, onFailure)
	
	fun getOrNull(): T? = result.getOrNull()
	fun getOrThrow(): T = result.getOrThrow()
	
	inline fun onSuccess(action: (T) -> Unit): CatchingResult<T> = also {
		result.onSuccess(action)
	}
	
	inline fun onFailure(action: (Throwable) -> Unit): CatchingResult<T> = also {
		result.onFailure(action)
	}
	
	inline fun <R> map(transform: (T) -> R): CatchingResult<R> =
		CatchingResult(result.map(transform))
	
	//endregion
}

/* 涉及 T : R 的，无法作为成员函数 */

/**
 * 如果异常为 [E]，对 [T] 进行 [transform]，返回新的 [CatchingResult]。
 *
 * 注意不要使用尖括号声明类型 [E]，如 `recoverException<IOException>()`。
 * 请使用 lambda 参数来声明异常类型，编译器会推断全部三个泛型。
 *
 * 示例：
 *
 * ```kotlin
 * trace.catching { error("Error") }
 *     .recoverException { e: IllegalStateException -> e.message }
 * ```
 */
inline fun <reified E : Throwable, R, T : R> CatchingResult<T>.recoverException(
	transform: (E) -> R
) = recover { if (it is E) transform(it) else it }

//region Result 的 API 转发

inline fun <R, T : R> CatchingResult<T>.getOrElse(onFailure: (Throwable) -> R): R =
	result.getOrElse(onFailure)

fun <R, T : R> CatchingResult<T>.getOrDefault(defaultValue: R): R =
	result.getOrDefault(defaultValue)

inline fun <R, T : R> CatchingResult<T>.recover(transform: (Throwable) -> R): CatchingResult<R> =
	CatchingResult(result.recover(transform))

//endregion
