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

package io.github.autotweaker.core.adapter.config

import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.data.settings.ConfigRegistry
import io.github.autotweaker.core.data.settings.Settings

object AppConfigAPI {
	fun list(): List<CoreConfig.AppConfig> = Settings.getAll().map { CoreConfig.AppConfig(it) }
	
	fun defaults(): List<CoreConfig.AppConfig> = ConfigRegistry.getAll()
		.map { (id, def) -> CoreConfig.AppConfig(SettingEntry(id, def.default, def.description)) }
	
	fun set(id: String, value: SettingValue) = Settings.set(id, value)
	
	fun setDesc(id: String, description: String) = Settings.setDescription(id, description)
}