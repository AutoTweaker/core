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

package io.github.autotweaker.core.adapter.i18n.translation

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.data.ResourcesLoader

object TranslateSettings {
	@AutoService(SettingDef::class)
	class BatchSize : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(40)
		override val description = "每批次翻译的条目数"
	}
	
	@AutoService(SettingDef::class)
	class SystemPrompt : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString(ResourcesLoader.loadPrompt("translate_system"))
		override val description = "用于翻译请求的系统提示模板，需要 {{target_language}} 变量"
	}
	
	@AutoService(SettingDef::class)
	class UserPrompt : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString(ResourcesLoader.loadPrompt("translate_user"))
		override val description = "用于翻译的用户请求目标，需要 {{target_language}} 和 {{content_to_translate}} 变量"
	}
	
	@AutoService(SettingDef::class)
	class MaxConcurrent : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(3)
		override val description = "同时翻译的最多批次数量"
	}
}
