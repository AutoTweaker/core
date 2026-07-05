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

import io.github.autotweaker.api.base.Mutable.Companion.forceMutable
import io.github.autotweaker.api.base.Mutable.Companion.mutable


/**
 * 包装一个不可变数据，基于 [ReentrantMutex] 提供并发安全的更新方式。同时支持在数据被修改时执行 lambda。
 *
 * 请不要使用此类包装本身可变的数据，[mutable] 及其重载会拒绝 [Mutable] / [MutableCollection] / [MutableMap]，但对于在这之外的类型，例如一个包含 `var` 的数据类、一个 [Mutable] 或 [MutableCollection] 的包装类，[mutable] 的检查无能为力。
 *
 * 请使用 [mutable] 来构造此类，如果确实需要包装 [Mutable]、[MutableCollection] 或 [MutableMap]，请使用 [forceMutable]，请阅读 [forceMutable] 的描述来了解这么做的影响，[forceMutable] 的描述也适用于包装其他可变类型带来的影响。
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
		 * 请不要对 [MutableCollection] 这类本身可变的数据调用 [mutable]。
		 *
		 * @param onChange 当数据被修改后执行的 lambda。
		 * @throws IllegalArgumentException [T] 的类型为 [Mutable] / [MutableCollection] / [MutableMap]。
		 */
		fun <T> T.mutable(onChange: suspend (old: T, new: T) -> Unit = { _, _ -> }): Mutable<T> {
			require(this !is Mutable<*>)
			require(this !is MutableCollection<*>)
			require(this !is MutableMap<*, *>)
			return Mutable(this, onChange)
		}
		
		/**
		 * 绕过 [mutable] 的安全检查，强制使用 [Mutable] 包装一个类型。
		 *
		 * 这意味着只要通过 [get] 或 [update] 拿到对数据的引用，就可随意更新而绕过 [update] 中的 mutex 保护和 [onChange]。
		 *
		 * [update] / [set] / [onChange] 在此类情况下仅适用于对于对象引用本身的更新。
		 */
		@Deprecated("Bypasses mutable checks, prefer mutable()")
		fun <T> T.forceMutable(onChange: suspend (old: T, new: T) -> Unit = { _, _ -> }): Mutable<T> =
			Mutable(this, onChange)
		
		
		/**
		 * 尽可能地在编译器拦截对 [Mutable] 的 [mutable] 调用，前提是匹配到此重载。
		 */
		@Deprecated("Already a Mutable object. Avoid nesting.", level = DeprecationLevel.ERROR)
		@Suppress("UnusedReceiverParameter")
		fun <T> Mutable<T>.mutable(): Nothing = throw UnsupportedOperationException()
		
		/**
		 * 尽可能地在编译器拦截对 [MutableCollection] 的 [mutable] 调用，前提是匹配到此重载。
		 */
		@Deprecated("Already a Mutable object. Avoid nesting.", level = DeprecationLevel.ERROR)
		@Suppress("UnusedReceiverParameter")
		fun <T> MutableCollection<T>.mutable(): Nothing = throw UnsupportedOperationException()
		
		/**
		 * 尽可能地在编译器拦截对 [MutableMap] 的 [mutable] 调用，前提是匹配到此重载。
		 */
		@Deprecated("Already a Mutable object. Avoid nesting.", level = DeprecationLevel.ERROR)
		@Suppress("UnusedReceiverParameter")
		fun <K, V> MutableMap<K, V>.mutable(): Nothing = throw UnsupportedOperationException()
	}
}
