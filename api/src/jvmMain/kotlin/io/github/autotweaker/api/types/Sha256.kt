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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.security.MessageDigest

/**
 * 表示一个 32 字节的 SHA-256 哈希值。
 */
@Serializable
class Sha256(val bytes: ByteArray) {
	init {
		require(bytes.size == 32) { "SHA256 hash must be 32 bytes, got ${bytes.size}" }
	}
	
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Sha256) return false
		return bytes.contentEquals(other.bytes)
	}
	
	override fun hashCode(): Int = bytes.contentHashCode()
	
	override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
	
	companion object : KSerializer<Sha256> {
		override val descriptor = PrimitiveSerialDescriptor("Sha256", PrimitiveKind.STRING)
		
		override fun serialize(encoder: Encoder, value: Sha256) =
			encoder.encodeString(value.toString())
		
		override fun deserialize(decoder: Decoder): Sha256 =
			fromString(decoder.decodeString())
		
		/**
		 * 从十六进制哈希值字符串构造 [Sha256]。
		 */
		fun fromString(hex: String): Sha256 {
			require(hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' })
			{ "Invalid SHA256 hex: $hex" }
			return Sha256(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
		}
		
		/**
		 * 计算一个二进制数据的 SHA-256 哈希值。
		 */
		fun hash(content: ByteArray): Sha256 {
			val digest = MessageDigest.getInstance("SHA-256").digest(content)
			return Sha256(digest)
		}
	}
}
