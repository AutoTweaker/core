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

@JvmInline
value class CatchingResult<T> @PublishedApi internal constructor(@PublishedApi internal val result: Result<T>) {
	inline fun <reified E : Throwable> onException(action: (E) -> Unit) =
		onFailure { if (it is E) action(it) }
	
	inline fun <reified E : Throwable> rethrow() =
		onException<E> { throw it }
	
	inline fun <reified E : Throwable> rethrow(on: (E) -> Unit) =
		onException<E> { on(it); throw it }
	
	
	inline fun onFailureIf(test: (Throwable) -> Boolean, action: (Throwable) -> Unit) =
		onFailure { if (test(it)) action(it) }
	
	inline fun rethrowIf(test: (Throwable) -> Boolean) =
		onFailureIf(test) { throw it }
	
	inline fun rethrowIf(test: (Throwable) -> Boolean, on: (Throwable) -> Unit) =
		onFailureIf(test) { on(it); throw it }
	
	
	inline fun <reified E : Throwable> onExceptionExcept(action: (Throwable) -> Unit) =
		onFailureIf({ it !is E }, action)
	
	inline fun <reified E : Throwable> rethrowNot() = rethrowIf { it !is E }
	
	//region Result的API转发
	
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
	
	inline fun <R> mapCatching(transform: (T) -> R): CatchingResult<R> =
		CatchingResult(result.mapCatching(transform))
	
	//endregion
}

inline fun <R, T : R> CatchingResult<T>.getOrElse(onFailure: (Throwable) -> R): R =
	result.getOrElse(onFailure)

fun <R, T : R> CatchingResult<T>.getOrDefault(defaultValue: R): R =
	result.getOrDefault(defaultValue)

inline fun <R, T : R> CatchingResult<T>.recover(transform: (Throwable) -> R): CatchingResult<R> =
	CatchingResult(result.recover(transform))

inline fun <R, T : R> CatchingResult<T>.recoverCatching(transform: (Throwable) -> R): CatchingResult<R> =
	CatchingResult(result.recoverCatching(transform))
