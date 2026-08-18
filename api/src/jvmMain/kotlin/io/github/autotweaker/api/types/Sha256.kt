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

package io.github.autotweaker.api.types

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import io.github.autotweaker.api.types.serializer.HashCodeSerializer
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * 表示一个 32 字节的 SHA-256 哈希值。
 */
@JvmInline
@Serializable
value class Sha256(@Serializable(with = HashCodeSerializer::class) val hash: HashCode) {
	init {
		require(hash.bits() == 256) { "SHA256 hash must be 256 bits, got ${hash.bits()}" }
	}
	
	/**
	 * @see HashCode.fromString
	 */
	constructor(hex: String) : this(HashCode.fromString(hex)) {
		require(hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' })
		{ "Invalid SHA256 hex: $hex" }
	}
	
	/**
	 * @see HashCode.fromBytes
	 */
	constructor(bytes: ByteArray) : this(HashCode.fromBytes(bytes)) {
		require(bytes.size == 32) { "SHA256 hash must be 32 bytes, got ${bytes.size}" }
	}
	
	val bytes: ByteArray get() = hash.asBytes()
	
	override fun toString(): String = hash.toString()
	
	companion object {
		/**
		 * 计算一个二进制数据的 SHA-256 哈希值。
		 */
		fun hash(input: ByteArray): Sha256 = Sha256(Hashing.sha256().hashBytes(input))
		
		/**
		 * 计算 [input] 中从 [off] 开始 [len] 字节的 SHA-256 哈希值。
		 */
		fun hash(input: ByteArray, off: Int, len: Int): Sha256 =
			Sha256(Hashing.sha256().hashBytes(input, off, len))
		
		/**
		 * 计算 [input] 剩余字节的 SHA-256 哈希值。
		 */
		fun hash(input: ByteBuffer): Sha256 = Sha256(Hashing.sha256().hashBytes(input))
		
		/**
		 * 计算 [input] 的 SHA-256 哈希值，按小端序解释。
		 */
		fun hash(input: Int): Sha256 = Sha256(Hashing.sha256().hashInt(input))
		
		/**
		 * 计算 [input] 的 SHA-256 哈希值，按小端序解释。
		 */
		fun hash(input: Long): Sha256 = Sha256(Hashing.sha256().hashLong(input))
		
		/**
		 * 计算 [input] 的 SHA-256 哈希值，每个 char 直接哈希，不做字符编码。
		 */
		fun hash(input: CharSequence): Sha256 = Sha256(Hashing.sha256().hashUnencodedChars(input))
		
		/**
		 * 计算 [input] 按 [charset] 编码后的 SHA-256 哈希值。
		 */
		fun hash(input: CharSequence, charset: Charset): Sha256 =
			Sha256(Hashing.sha256().hashString(input, charset))
	}
}
