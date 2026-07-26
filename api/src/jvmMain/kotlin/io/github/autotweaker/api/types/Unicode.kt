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

/**
 * 表示一个 Unicode 转义序列，可使用原始 [Char] 构造。
 */
@JvmInline
value class Unicode(private val value: Char) {
	/**
	 * 获取 Unicode 转义序列。
	 */
	override fun toString() = String.format("\\u%04X", value.code)
	
	/**
	 * 获取原始字符。
	 */
	fun toChar(): Char = value
	
	/**
	 * 获取 Unicode 码点值。
	 */
	fun codePoint(): Int = value.code
	
	companion object {
		/**
		 * 从码点创建 Unicode 转义序列。
		 */
		fun Int.toUnicode(): Unicode {
			require(this in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
				"Code point out of range for \\uXXXX format: $this"
			}
			return Unicode(toChar())
		}
	}
}
