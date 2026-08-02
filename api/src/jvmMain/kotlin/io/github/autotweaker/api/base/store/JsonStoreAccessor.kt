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

import io.github.autotweaker.api.storage.JsonStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 基于 JsonStore 的持久化工具类，处理序列化、反序列化、默认值、懒加载。
 *
 * @param store 使用方实现 [io.github.autotweaker.api.JsonStorable] 后将 store 传入即可。
 * @param serializer 用于序列化和反序列化 [V] 的序列化器。
 * @param default 提供数据库中无已有数据时的默认值，也就是初始值。
 */
class JsonStoreAccessor<V>(
	private val store: JsonStore,
	private val serializer: KSerializer<V>,
	private val default: () -> V,
) {
	/**
	 * 初始值，仅在访问时加载一次，请自行在内存中维护当前值。
	 */
	val initial: V by lazy { load() ?: default() }
	
	/**
	 * 将数据序列化后存储到硬盘。
	 */
	fun save(value: V) =
		store.set(Json.encodeToJsonElement(serializer, value))
	
	private fun load(): V? =
		store.get()?.let { Json.decodeFromJsonElement(serializer, it) }
}
