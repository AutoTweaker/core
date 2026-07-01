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

package io.github.autotweaker.core.infrastructure.i18n

import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.core.PluginLoader
import java.util.*

object I18nRegistry {
	private val _defs: Map<String, I18nDef> = run {
		val map = mutableMapOf<String, I18nDef>()
		for (def in ServiceLoader.load(I18nDef::class.java)) {
			val key = def::class.qualifiedName
				?: throw IllegalStateException("Anonymous I18nDef not allowed: $def")
			map[key] = def
		}
		for (def in PluginLoader.load<I18nDef>()) {
			val key = def::class.qualifiedName
				?: throw IllegalStateException("Anonymous I18nDef not allowed: $def")
			map[key] = def
		}
		return@run map
	}
	
	fun get(key: String): I18nDef? = _defs[key]
	fun getAll(): Map<String, I18nDef> = _defs.toMap()
}
