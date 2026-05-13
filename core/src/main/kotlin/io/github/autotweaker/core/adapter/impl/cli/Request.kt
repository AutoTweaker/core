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

package io.github.autotweaker.core.adapter.impl.cli

data class Request(
	val values: Map<String, String>,
	val positional: List<String>,
	val prog: String = "autotweaker",
	private val aliasToCanonical: Map<String, String> = emptyMap(),
) {
	fun get(name: String): String? = values[name] ?: aliasToCanonical[name]?.let { values[it] }
	
	fun has(name: String): Boolean = name in values || aliasToCanonical[name] in values
}
