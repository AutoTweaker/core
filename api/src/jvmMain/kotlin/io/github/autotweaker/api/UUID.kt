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

@file:Suppress("NOTHING_TO_INLINE")

package io.github.autotweaker.api

import java.nio.ByteBuffer
import java.util.*

/**
 * `UUID.randomUUID()` 的别名。
 */
inline fun UUID(): UUID = UUID.randomUUID()

/**
 * [UUID] 转 [ByteArray]。
 */
fun UUID.toByteArray(): ByteArray =
	ByteBuffer.allocate(16)
		.putLong(mostSignificantBits)
		.putLong(leastSignificantBits)
		.array()

/**
 * 用 16 位 [ByteArray] 构造 [UUID]。
 *
 * @throws IllegalArgumentException [bytes] 长度不是 16 位
 */
fun UUID(bytes: ByteArray): UUID {
	require(bytes.size == 16) { "UUID requires exactly 16 bytes" }
	
	val buffer = ByteBuffer.wrap(bytes)
	return UUID(buffer.long, buffer.long)
}
