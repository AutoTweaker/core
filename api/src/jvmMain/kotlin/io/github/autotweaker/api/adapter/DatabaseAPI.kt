/*
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

package io.github.autotweaker.api.adapter

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 基于 H2 的数据库服务，可连接到多个独立的数据库，并自由读写。不同插件之间的库文件隔离，按 [Adapter] 类 FQN 区分。
 *
 * 对于常规持久化需求，可采用 [io.github.autotweaker.api.store.JsonStore] 等服务。
 */
fun interface DatabaseAPI {
	/**
	 * 连接到一个数据库，多次连接同名数据库会得到同一 [Database] 对象。
	 *
	 * @param name 数据库的名称，不要包含文件后缀，可以包含 `/` 来使用子目录。不能包含 `;` 或 `..`。
	 * @throws IllegalArgumentException [name] 为空字符串，或包含 `;`、`..`。
	 */
	fun open(name: String): Database
}
