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

package io.github.autotweaker.api.config

import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue

interface SettingService {
	operator fun <V : SettingValue<T>, T> invoke(def: SettingDef<V>): T
	
	fun getAll(): List<SettingEntry>
	fun getDef(id: String): SettingDef<*>?
	
	fun <V : SettingValue<*>> set(def: SettingDef<V>, value: V)
	fun set(id: String, value: SettingValue<*>)
	fun setDescription(id: String, description: String)
}
