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

import io.github.autotweaker.api.types.Unicode.Companion.toUnicode


/**
 * 表示一个 Unicode 转义序列，建议使用 [toUnicode] 构造。
 *
 * @throws IllegalArgumentException [value] 不是一个合法的 Unicode 转义序列。
 */
@JvmInline
value class Unicode(val value: String) {
	init {
		require(isValid(value)) { "Invalid Unicode escape sequence: $value" }
	}
	
	override fun toString() = value
	
	/**
	 * 解码为对应的字符
	 */
	fun toChar(): Char = hexValue().toChar()
	
	/**
	 * 获取 Unicode 码点值
	 */
	fun codePoint(): Int = hexValue()
	
	private fun hexValue(): Int = value.substring(2).toInt(16)
	
	companion object {
		/**
		 * 从 [Char] 创建 Unicode 转义序列
		 */
		fun Char.toUnicode(): Unicode =
			Unicode("\\u${code.toHex4()}")
		
		/**
		 * 从码点创建 Unicode 转义序列
		 */
		fun Int.toUnicode(): Unicode {
			require(this in 0..0xFFFF) { "Code point out of range for \\uXXXX format: $this" }
			return Unicode("\\u${toHex4()}")
		}
		
		private fun Int.toHex4(): String = toString(16).padStart(4, '0')
		
		/**
		 * @return 字符串是否是一个有效的 Unicode 转义序列
		 */
		fun isValid(input: String): Boolean {
			if (input.length != 6) return false
			if (!input.startsWith("\\u")) return false
			return input.substring(2)
				.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
		}
	}
}
