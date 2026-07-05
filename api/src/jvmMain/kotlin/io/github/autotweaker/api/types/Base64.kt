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

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64 as KBase64

@Deprecated("不应该在内存中把一段base64字符串传来传去")
@Serializable
@JvmInline
value class Base64(val value: String) {
	init {
		require(isValid(value)) { "Invalid Base64 string" }
	}
	
	fun decode(): ByteArray = KBase64.decode(value)
	
	companion object {
		fun encode(bytes: ByteArray): Base64 = Base64(KBase64.encode(bytes))
		
		fun isValid(input: String): Boolean {
			if (input.length % 4 != 0) return false
			val padding = input.takeLastWhile { it == '=' }.length
			if (padding > 2) return false
			return input.dropLast(padding).all { it.isLetterOrDigit() || it == '+' || it == '/' }
		}
	}
}
