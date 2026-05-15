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
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.data.settings.Settings

object AppConfigAPI {
	private val cfg = Settings
	
	fun get(id: SettingKey): CoreConfig.AppConfig? =
		cfg.get().find { it.key == id }?.let { CoreConfig.AppConfig(it) }
	
	fun set(setting: CoreConfig.AppConfig) = cfg.set(setting.setting)
	fun getAll(): List<CoreConfig.AppConfig> = cfg.get().map { CoreConfig.AppConfig(it) }
}