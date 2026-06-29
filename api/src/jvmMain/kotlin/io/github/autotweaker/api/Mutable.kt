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

import io.github.autotweaker.api.Mutable.Companion.mutable


/**
 * 包装一个不可变数据，并提供并发安全的更新方式。
 *
 * 支持在数据被修改时执行 lambda。
 *
 * 请使用 [mutable] 来构造此类。
 */
class Mutable<T> private constructor(
	@Volatile private var value: T,
	private val onChange: suspend (old: T, new: T) -> Unit
) {
	private val lock = ReentrantMutex()
	
	/**
	 * 获取当前值，无锁。
	 */
	fun get(): T = value
	
	/**
	 * 使用 [transform] 的返回值来更新值，返回更新后的值，[transform] 内的表达式在锁内执行，并发安全。
	 *
	 * 内部使用 [ReentrantMutex]，虽然应该不会有人在 [update] 里面 [update]，但这不会导致死锁。
	 *
	 * @see ReentrantMutex
	 */
	suspend fun update(transform: suspend (T) -> T): T = lock.withLock {
		set(transform(value))
	}
	
	
	/**
	 * 覆盖当前值，返回修改后的新值。
	 */
	suspend fun set(new: T): T = lock.withLock {
		val old = value
		value = new
		onChange(old, new)
		return@withLock value
	}
	
	companion object {
		/**
		 * 将一个不可变数据包装为可变数据，并提供并发安全的更新方式。
		 *
		 * 请不要对 [MutableList] 这类本身可变的数据调用 [mutable]，这只会带来混乱。
		 *
		 * @param onChange 当数据被修改时执行的 lambda。
		 * @throws IllegalArgumentException 对 [Mutable] 调用 [mutable]。但不包括对 [MutableList] 这类可变数据调用，请自行避免此类调用。
		 */
		fun <T> T.mutable(onChange: (old: T, new: T) -> Unit = { _, _ -> }): Mutable<T> {
			require(this !is Mutable<*>)
			return Mutable(this, onChange)
		}
		
		/**
		 * 尽可能地在编译器拦截对 [Mutable] 的 [mutable] 调用，前提是匹配到此重载。
		 */
		@Deprecated("Already a Mutable object. Avoid nesting.", level = DeprecationLevel.ERROR)
		@Suppress("UnusedReceiverParameter")
		fun <T> Mutable<T>.mutable(): Nothing = throw UnsupportedOperationException()
	}
}
