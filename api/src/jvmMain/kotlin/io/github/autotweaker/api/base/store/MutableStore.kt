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

package io.github.autotweaker.api.base.store

import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.store

/**
 * 使用 [V] 为缓存，[io.github.autotweaker.api.storage.JsonStore] 为持久化服务的存储基类。
 *
 * [MutableStore] 有锁（基于 [ReentrantMutex]），但缓存的对象不能被整个替换，[transform] 块中可以在锁内干任何事情，也有协程上下文，适合更新值的相关逻辑拥有一些复杂逻辑的场景。
 *
 * [V] 应为一个可变的类型，例如 [MutableCollection]，对于 [V] 本身的引用是不可被修改的。要存储一个不可变类型，请使用 [ImmutableStore]。
 */
abstract class MutableStore<V> : StoreBase<V>() {
	private val accessor by lazy { JsonStoreAccessor(store, serializer, ::default) }
	private val lock = ReentrantMutex()
	private val cache: V by lazy { accessor.initial }
	
	/**
	 * 在锁内执行 [block]，[block] 中可以包含对缓存的访问或更新，[block] 完成后会自动落盘。
	 *
	 * @param block 输入为内存中的 Mutable 对象，输出作为 [transform] 返回值。不要把输入带出 [block]，例如赋值给一个外部变量，或直接作为 [block] 的返回值，这将导致对缓存的修改不会被及时保存，或永远不会被保存。
	 */
	protected suspend fun <R> transform(block: suspend (V) -> R): R = lock.withLock {
		block(cache).also { accessor.save(cache) }
	}
}
