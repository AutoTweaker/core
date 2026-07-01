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

package io.github.autotweaker.core.infrastructure.i18n.translation

object PlaceholderValidator {
	private val positionalRegex = Regex("""%\d+\$[a-zA-Z]""")
	private val nonPositionalRegex = Regex("""%[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
	
	fun validate(source: String, translated: String): Boolean {
		if (translated.isBlank()) return false
		
		val srcPos = positionalRegex.findAll(source).map { it.value }.toSet()
		val tgtPos = positionalRegex.findAll(translated).map { it.value }.toSet()
		if (srcPos != tgtPos) return false
		
		val srcNonPosCount = nonPositionalRegex.findAll(source).count()
		val tgtNonPosCount = nonPositionalRegex.findAll(translated).count()
		return srcNonPosCount == tgtNonPosCount
	}
}
