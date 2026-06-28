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
import io.github.autotweaker.api.types.KebabId.Companion.toKebabId
import kotlinx.serialization.Serializable

/**
 * 表示一个 kebab-case 格式的 [String]。
 */
@JvmInline
@Serializable
value class KebabId private constructor(val value: String) {
	override fun toString(): String = value
	
	companion object : Traceable {
		/**
		 * [this] 不能为空，只能包含小写字母和短横线（`-`）。
		 *
		 * @throws IllegalArgumentException [this] 不合法。
		 */
		fun String.toKebabId(): KebabId {
			require(!isEmpty())
			require(all { it.isLowerCase() || it == '-' })
			return KebabId(this)
		}
		
		/**
		 * @return 如果格式合法，返回 [KebabId]，否则返回 null。
		 * @see toKebabId
		 */
		fun String.toKebabIdOrNull(): KebabId? = trace.catching { toKebabId() }.getOrNull()
	}
}
