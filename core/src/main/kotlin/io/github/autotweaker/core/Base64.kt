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

@file:JvmName("Base64")

package io.github.autotweaker.core

import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64 as KBase64

@JvmInline
value class Base64(val value: String) {
	init {
		require(isValid(value)) { "Invalid Base64 string" }
	}
	
	@OptIn(ExperimentalEncodingApi::class)
	fun decode(): ByteArray = KBase64.decode(value)
	
	companion object {
		@OptIn(ExperimentalEncodingApi::class)
		fun encode(bytes: ByteArray): Base64 = Base64(KBase64.encode(bytes))
		
		fun isValid(input: String): Boolean {
			if (input.length % 4 != 0) return false
			return input.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
		}
	}
}
