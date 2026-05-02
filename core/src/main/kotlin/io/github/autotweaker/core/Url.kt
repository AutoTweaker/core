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

@file:JvmName("Url")

package io.github.autotweaker.core

import kotlinx.serialization.Serializable
import java.net.URI

@JvmInline
@Serializable
value class Url private constructor(val value: String) {
	companion object {
		operator fun invoke(raw: String): Url {
			val trimmed = raw.trim().trimEnd('/')
			require(trimmed.isNotBlank()) { "URL must not be blank" }
			runCatching { URI(trimmed) }.getOrNull()
				?.takeIf { it.isAbsolute && it.scheme in listOf("http", "https") }
				?: throw IllegalArgumentException("Invalid URL: $trimmed")
			return Url(trimmed)
		}
	}
}
