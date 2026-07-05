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

import io.github.autotweaker.api.JsonStorable
import kotlinx.serialization.KSerializer

/**
 * 此类的子类无需显式实现 [JsonStorable]，需要实现 [serializer] / [default]。
 *
 * 很简单的抽象类，只为让 [AtomicStore] / [ImmutableStore] / [MutableStore] 省几行重复代码。
 */
abstract class StoreBase<V> : JsonStorable {
	/**
	 * 提供用于 [V] 的序列化器。
	 */
	protected abstract val serializer: KSerializer<V>
	
	/**
	 * 数据库中无已有数据时的默认值，也就是初始值。
	 */
	protected abstract fun default(): V
}
