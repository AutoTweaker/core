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
import io.github.autotweaker.api.base.BooleanSetting
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader

object TranslateSettings {
	@AutoService(SettingDef::class)
	class BatchSize : IntSetting(
		40, "每批次翻译的条目数"
	)
	
	@AutoService(SettingDef::class)
	class SystemPrompt : StringSetting(
		ResourcesLoader.loadPrompt("translate_system"), "用于翻译请求的系统提示模板，需要 {{target_language}} 变量"
	)
	
	@AutoService(SettingDef::class)
	class UserPrompt : StringSetting(
		ResourcesLoader.loadPrompt("translate_user"),
		"用于翻译的用户请求模板，需要 {{target_language}} 和 {{content_to_translate}} 变量"
	)
	
	@AutoService(SettingDef::class)
	class MaxConcurrent : IntSetting(
		3, "同时翻译的最多批次数量"
	)
	
	@AutoService(SettingDef::class)
	class Thinking : BooleanSetting(
		false, "翻译请求是否启用思考"
	)
}
