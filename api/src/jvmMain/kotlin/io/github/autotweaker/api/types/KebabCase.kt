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

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.trace
import kotlinx.serialization.Serializable

/**
 * 表示一个 kebab-case 格式的 [String]。
 */
@JvmInline
@Serializable
value class KebabCase private constructor(val value: String) {
	override fun toString(): String = value
	
	companion object : Traceable {
		/**
		 * [this] 只能包含小写字母、数字和短横线（`-`），不能为空，不能以 `-` 开头或结尾，不能连续多个 `-`。
		 *
		 * @throws IllegalArgumentException [this] 不合法。
		 */
		fun String.toKebab(): KebabCase {
			require(isNotEmpty()) { "Kebab case must not be empty" }
			require(first() != '-' && last() != '-') { "Kebab case must not start or end with '-': $this" }
			require(all { it.isLowerCase() || it.isDigit() || it == '-' }) {
				"Kebab case must only contain lowercase letters, digits and '-': $this"
			}
			require(!contains("--")) { "Kebab case must not contain consecutive '-': $this" }
			return KebabCase(this)
		}
		
		/**
		 * @return 如果格式合法，返回 [KebabCase]，否则返回 null。
		 * @see toKebab
		 */
		fun String.toKebabOrNull(): KebabCase? = trace.catching { toKebab() }.getOrNull()
	}
}
