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
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.UpperSnakeCase.Companion.toUpperSnake
import kotlinx.serialization.Serializable

/**
 * 表示一个 UPPER_SNAKE_CASE 格式的 [String]。
 */
@JvmInline
@Serializable
value class UpperSnakeCase private constructor(val value: String) {
	override fun toString(): String = value
	
	companion object : Traceable {
		/**
		 * [this] 只能包含大写字母、数字和下划线（`_`），不能为空，不能以 `_` 开头或结尾，不能连续多个 `_`。
		 *
		 * @throws IllegalArgumentException [this] 不合法。
		 */
		fun String.toUpperSnake(): UpperSnakeCase {
			require(isNotEmpty())
			require(first() != '_' && last() != '_')
			require(all { it.isUpperCase() || it.isDigit() || it == '_' })
			require(!contains("__"))
			return UpperSnakeCase(this)
		}
		
		/**
		 * @return 如果格式合法，返回 [UpperSnakeCase]，否则返回 null。
		 * @see toUpperSnake
		 */
		fun String.toUpperSnakeOrNull(): UpperSnakeCase? = trace.catching { toUpperSnake() }.getOrNull()
	}
}
