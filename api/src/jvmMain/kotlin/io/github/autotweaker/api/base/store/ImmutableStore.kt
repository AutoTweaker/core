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

import io.github.autotweaker.api.base.Mutable
import io.github.autotweaker.api.base.Mutable.Companion.mutable
import io.github.autotweaker.api.store

/**
 * 使用 [Mutable] 为缓存，[io.github.autotweaker.api.config.JsonStore] 为持久化服务的存储基类。
 *
 * 通过 [cache] 即可调用 [Mutable] 的 api，数据通过 [Mutable] 的 `onChange` 回调自动保存。
 *
 * [ImmutableStore] 有锁，[Mutable.update] 的 lambda 在锁内执行，可以有副作用，也有协程上下文，适合更新值的相关逻辑拥有一些复杂逻辑的场景。
 *
 * [V] 应为一个不可变的类型，例如一个 **不** 包含 `var` 的数据类，或 [java.util.UUID] 这类不可直接变更值的类型，反例：[MutableCollection]。如果需要包装可变类型，请使用 [MutableStore]。
 */
abstract class ImmutableStore<V> : StoreBase<V>() {
	private val accessor by lazy { JsonStoreAccessor(store, serializer, ::default) }
	
	/**
	 * 通过 [Mutable] 包装的值，可以通过 [Mutable] 的 api 更新此值。
	 */
	protected val cache: Mutable<V> by lazy {
		accessor.initial.mutable { _, new -> accessor.save(new) }
	}
}
