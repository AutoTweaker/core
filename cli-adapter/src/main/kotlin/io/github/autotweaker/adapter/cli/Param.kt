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

package io.github.autotweaker.adapter.cli

import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.i18n.I18nService

sealed class Param {
	abstract val name: String
	abstract val description: String
	abstract val aliases: List<String>
	abstract fun format(): String
	
	data class Flag(
		override val name: String,
		override val description: String,
		override val aliases: List<String>,
	) : Param() {
		constructor(name: String, description: String) : this(
			name, description,
			if (name.length > 1) listOf(name[0].toString()) else emptyList(),
		)
		
		
		override fun format(): String {
			val all = listOf(name) + aliases
			val parts = all.map { if (it.length == 1) "-$it" else "--$it" }
			return parts.joinToString(", ")
		}
	}
	
	data class Value(
		override val name: String,
		override val description: String,
		override val aliases: List<String>,
	) : Param() {
		constructor(name: String, description: String) : this(
			name, description,
			if (name.length > 1) listOf(name[0].toString()) else emptyList(),
		)
		
		override fun format(): String {
			val all = listOf(name) + aliases
			val parts = all.map { if (it.length == 1) "-$it" else "--$it" }
			return "${parts.joinToString(", ")} <value>"
		}
	}
	
	data class Positional(
		override val name: String,
		override val description: String,
	) : Param() {
		override val aliases: List<String> = emptyList()
		override fun format(): String = "<$name>"
	}
	
	enum class Type { FLAG, VALUE, POSITIONAL }
	
	companion object {
		fun fromI18n(i18n: I18nService, type: Type, name: String, desc: I18nDef, aliases: List<String>): Param =
			when (type) {
				Type.FLAG -> Flag(name, i18n.get(desc), aliases)
				Type.VALUE -> Value(name, i18n.get(desc), aliases)
				Type.POSITIONAL -> Positional(name, i18n.get(desc))
			}
		
		fun fromI18n(i18n: I18nService, type: Type, name: String, desc: I18nDef): Param =
			when (type) {
				Type.FLAG -> Flag(name, i18n.get(desc))
				Type.VALUE -> Value(name, i18n.get(desc))
				Type.POSITIONAL -> Positional(name, i18n.get(desc))
			}
	}
}
