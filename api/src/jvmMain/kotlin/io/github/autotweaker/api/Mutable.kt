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
 * 包装一个不可变属性，并提供并发安全的更新方式。
 *
 * 请使用 [mutable] 来构造此类。
 */
class Mutable<T> private constructor(@Volatile private var value: T) {
	private val lock = ReentrantMutex()
	
	fun get(): T = value
	fun set(new: T): Mutable<T> = also { value = new }
	
	/**
	 * 更新值，[transform] 内的 lambda 在锁内执行，并发安全。
	 *
	 * 内部使用 [ReentrantMutex]，虽然应该不会有人在 [update] 里面 [update]，但这不会导致死锁。
	 */
	suspend fun update(transform: suspend (T) -> T): Mutable<T> = also {
		lock.withLock {
			value = transform(value)
		}
	}
	
	/**
	 * 返回一个可空类型的 [Mutable] 对象。
	 */
	fun nullable(): Mutable<T?> = Mutable(value)
	
	companion object {
		/**
		 * 将一个不可变属性包装为可变属性。
		 *
		 * 请不要对 [MutableList] 这类本身可变的属性调用 [mutable]，这只会带来混乱。
		 *
		 * @throws IllegalArgumentException 对 [Mutable] 调用 [mutable]。
		 */
		fun <T> T.mutable(): Mutable<T> {
			require(this !is Mutable<*>)
			return Mutable(this)
		}
		
		@Deprecated("Already a Mutable object. Avoid nesting.", level = DeprecationLevel.ERROR)
		@Suppress("UnusedReceiverParameter")
		fun <T> Mutable<T>.mutable(): Nothing = throw UnsupportedOperationException()
	}
}
