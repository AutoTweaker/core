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

package io.github.autotweaker.api

/**
 * 从一个可能包含 Unicode 转义序列的字符串中解码 Unicode 转义。
 *
 * 还支持反斜杠转义（`\\` 解码为 `\`），除此之外不支持任何转义（如 `\n`）。
 *
 * 除非 [strict] 否则将在遇到未知转义标记或不合法的 Unicode 转义时抛出 [IllegalStateException]，包含可读的 message。
 */
fun String.unescapeUnicode(strict: Boolean = true): String {
	if (isBlank()) return this
	
	val out = StringBuilder()
	
	var i = 0
	while (i < length) {
		if (this[i] != '\\') {
			out.append(this[i++])
			continue
		}
		
		if (i + 1 >= length)
			if (strict) error("Trailing backslash at position $i") else break
		
		when (this[i + 1]) {
			'\\' -> {
				out.append('\\')
				i += 2
			}
			
			'u' -> {
				if (i + 5 >= length) {
					if (strict) error("Incomplete Unicode escape sequence at position $i: expected 4 hex digits after \\u")
					out.append('\\')
					i++
				} else {
					val hex = substring(i + 2, i + 6)
					val code = hex.toIntOrNull(16)
					if (code == null) {
						if (strict) error("Invalid Unicode escape sequence \\u$hex at position $i: not a valid hex number")
						out.append('\\')
						i++
					} else {
						out.append(code.toChar())
						i += 6
					}
				}
			}
			
			else -> {
				if (strict) error("Unknown escape sequence \\${this[i + 1]} at position $i")
				out.append('\\')
				i++
			}
		}
	}
	
	return out.toString()
}

/**
 * 对一个字符进行 Unicode 转义，返回一个 Unicode 转义序列。
 */
fun Char.toUnicodeEscape(): String =
	"\\u%04X".format(code)

/**
 * 对一个字符串进行 Unicode 转义，返回 6 倍大小的新字符串。
 */
fun String.toUnicodeEscape(): String =
	buildString(length * 6) {
		for (c in this@toUnicodeEscape) {
			append(c.toUnicodeEscape())
		}
	}
