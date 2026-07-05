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

package io.github.autotweaker.api.storage

import kotlinx.serialization.json.JsonElement

/**
 * 基于 H2 数据库的 Json 读写服务，可通过 [io.github.autotweaker.api.JsonStorable] 接口获取。
 *
 * 每个类都拥有自己的独立命名空间，读写 `AppConfig` 数据库 `json_store` 表中以自身 [Class.name] 为键的一行。
 *
 * 此接口直接读写数据库，建议继承 [io.github.autotweaker.api.base.store.ImmutableStore] / [io.github.autotweaker.api.base.store.MutableStore] / [io.github.autotweaker.api.base.store.AtomicStore]，这些基类会处理序列化、反序列化、缓存、懒加载、缓存落盘，关于如何选用请参见基类文档。
 */
interface JsonStore {
	/**
	 * 读取 Json，从未保存或数据损坏返回 null。
	 */
	fun get(): JsonElement?
	
	/**
	 * 保存 Json，覆盖旧值。
	 */
	fun set(value: JsonElement)
}
