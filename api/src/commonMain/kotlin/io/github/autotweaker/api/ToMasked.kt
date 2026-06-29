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
 * 给敏感信息例如 api key 打码。
 *
 * 前 [prefixKeep] 字符和后 [suffixKeep] 字符将被保留，中间部分将替换为等长 [MASK_CHAR]。
 *
 * @param minLength 小于或等于此长度的 [this] 将直接输出等长 [MASK_CHAR]
 */
fun String.toMasked(
	minLength: Int = 15,
	prefixKeep: Int = 5,
	suffixKeep: Int = 4,
): String {
	if (length <= minLength) return MASK_CHAR * length
	
	return buildString(length) {
		this@toMasked.forEachIndexed { index, char ->
			if (index < prefixKeep || index > lastIndex - suffixKeep)
				append(char)
			else
				append(MASK_CHAR)
		}
	}
}
