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

import io.github.autotweaker.api.types.Sha256

/**
 * 基于 H2 数据库，内容寻址的对象存储服务，适用于存储二进制数据。
 *
 * 所有数据都会被存储到 `Objects` 数据库的 `objects` 表，并通过 SHA-256 去重。
 *
 * 不具备自动清理机制，也不支持删除数据。
 */
interface ObjectStorage {
	/**
	 * 保存一个二进制数据到数据库。
	 *
	 * @return [bytes] 的 SHA-256，作为数据的标识符。
	 */
	suspend fun put(bytes: ByteArray): Sha256
	
	/**
	 * 通过数据的 SHA-256 获取二进制内容。
	 *
	 * @return 找不到数据返回 null。
	 */
	suspend fun get(sha256: Sha256): ByteArray?
}
