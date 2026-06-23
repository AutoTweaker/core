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

package io.github.autotweaker.core.infrastructure.persistence.config

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.core.PluginLoader
import java.util.*

object ConfigRegistry {
	private val _defs: Map<String, SettingDef<*>> = run {
		val map = mutableMapOf<String, SettingDef<*>>()
		for (def in ServiceLoader.load(SettingDef::class.java)) {
			val id = def::class.qualifiedName ?: throw IllegalStateException("Anonymous SettingDef not allowed: $def")
			map[id] = def
		}
		for (def in PluginLoader.load<SettingDef<*>>()) {
			val id = def::class.qualifiedName ?: throw IllegalStateException("Anonymous SettingDef not allowed: $def")
			map[id] = def
		}
		return@run map
	}
	
	fun get(id: String): SettingDef<*>? = _defs[id]
	
	fun getAll(): Map<String, SettingDef<*>> = _defs
}
