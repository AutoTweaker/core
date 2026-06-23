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


inline fun <T, reified E : Throwable> Result<T>.onException(action: (E) -> Unit) =
	onFailure { if (it is E) action(it) }

inline fun <T, reified E : Throwable> Result<T>.rethrow() =
	onException<T, E> { throw it }

@JvmName("rethrowTyped")
inline fun <T, reified E : Throwable> Result<T>.rethrow(on: (E) -> Unit) =
	onException<T, E> { e ->
		on(e)
		throw e
	}


inline fun <T> Result<T>.onException(test: (Throwable) -> Boolean, action: (Throwable) -> Unit) =
	onFailure { if (test(it)) action(it) }

inline fun <T> Result<T>.rethrow(test: (Throwable) -> Boolean) =
	onException(test) { throw it }

inline fun <T> Result<T>.rethrow(test: (Throwable) -> Boolean, on: (Throwable) -> Unit) =
	onException(test) { e ->
		on(e)
		throw e
	}
