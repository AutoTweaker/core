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

package io.github.autotweaker.core.data.settings

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SettingKey private constructor(val value: String) {
	companion object {
		private val SEGMENT_PATTERN = Regex("^[a-z0-9]{2,}$")
		
		operator fun invoke(raw: String): SettingKey {
			require(raw.isNotBlank()) { "SettingKey must not be blank" }
			require(!raw.startsWith('.')) { "SettingKey must not start with '.'" }
			require(!raw.endsWith('.')) { "SettingKey must not end with '.'" }
			val segments = raw.split('.')
			require(segments.all { SEGMENT_PATTERN.matches(it) }) {
				"Each segment must be 2+ lowercase letters or digits, got: $raw"
			}
			return SettingKey(raw)
		}
	}
}
