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

fun String.unescapeUnicode(strict: Boolean = false): String {
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


fun Char.toUnicodeEscape(): String =
	"\\u%04X".format(code)

fun String.toUnicodeEscape(): String =
	buildString(length * 6) {
		for (c in this@toUnicodeEscape) {
			append(c.toUnicodeEscape())
		}
	}
