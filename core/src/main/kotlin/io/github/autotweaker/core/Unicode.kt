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

@file:JvmName("Unicode")

package io.github.autotweaker.core

@Suppress("unused")
@JvmInline
value class Unicode(val value: String) {
	init {
		require(isValid(value)) { "Invalid Unicode escape sequence: $value" }
	}
	
	/** 解码为对应的字符 */
	fun toChar(): Char = value.substring(2).toInt(16).toChar()
	
	/** 获取 Unicode 码点值 */
	fun codePoint(): Int = value.substring(2).toInt(16)
	
	companion object {
		/** 从字符创建 Unicode 转义序列 */
		fun fromChar(char: Char): Unicode =
			Unicode("\\u${char.code.toString(16).padStart(4, '0')}")
		
		/** 从码点创建 Unicode 转义序列 */
		fun fromCodePoint(codePoint: Int): Unicode {
			require(codePoint in 0..0xFFFF) { "Code point out of range for \\uXXXX format: $codePoint" }
			return Unicode("\\u${codePoint.toString(16).padStart(4, '0')}")
		}
		
		fun isValid(input: String): Boolean {
			if (input.length != 6) return false
			if (!input.startsWith("\\u")) return false
			return input.substring(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
		}
	}
}
