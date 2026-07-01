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

package io.github.autotweaker.adapter.cli.commands.config

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object CfgI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		Locale.ENGLISH to "Query and manage settings",
		Locale.SIMPLIFIED_CHINESE to "查询和管理应用设置",
	)
	
	@AutoService(I18nDef::class)
	class List : I18nBase(
		Locale.ENGLISH to "List all settings",
		Locale.SIMPLIFIED_CHINESE to "列出所有设置条目",
	)
	
	@AutoService(I18nDef::class)
	class Full : I18nBase(
		Locale.ENGLISH to "Show full setting details",
		Locale.SIMPLIFIED_CHINESE to "显示设置条目的完整信息",
	)
	
	@AutoService(I18nDef::class)
	class Limit : I18nBase(
		Locale.ENGLISH to "Limit number of entries",
		Locale.SIMPLIFIED_CHINESE to "仅显示指定数量的条目",
	)
	
	@AutoService(I18nDef::class)
	class Set : I18nBase(
		Locale.ENGLISH to "Modify a setting by key",
		Locale.SIMPLIFIED_CHINESE to "根据键修改指定设置项",
	)
	
	@AutoService(I18nDef::class)
	class SetValue : I18nBase(
		Locale.ENGLISH to "New value for the setting",
		Locale.SIMPLIFIED_CHINESE to "设置项的新值",
	)
	
	@AutoService(I18nDef::class)
	class Search : I18nBase(
		Locale.ENGLISH to "Search settings",
		Locale.SIMPLIFIED_CHINESE to "搜索设置条目",
	)
	
	@AutoService(I18nDef::class)
	class SearchKey : I18nBase(
		Locale.ENGLISH to "Search in setting keys",
		Locale.SIMPLIFIED_CHINESE to "在设置键中搜索",
	)
	
	@AutoService(I18nDef::class)
	class SearchValue : I18nBase(
		Locale.ENGLISH to "Search in setting values",
		Locale.SIMPLIFIED_CHINESE to "在设置值中搜索",
	)
	
	@AutoService(I18nDef::class)
	class SearchDesc : I18nBase(
		Locale.ENGLISH to "Search in setting descriptions",
		Locale.SIMPLIFIED_CHINESE to "在设置描述中搜索",
	)
	
	@AutoService(I18nDef::class)
	class Reset : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "重置指定的设置项",
	)
	
	@AutoService(I18nDef::class)
	class Yes : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "跳过重置确认",
	)
	
	@AutoService(I18nDef::class)
	class OutKey : I18nBase(
		Locale.ENGLISH to "Key: %s",
		Locale.SIMPLIFIED_CHINESE to "键名: %s",
	)
	
	@AutoService(I18nDef::class)
	class OutDesc : I18nBase(
		Locale.ENGLISH to "Description: %s",
		Locale.SIMPLIFIED_CHINESE to "描述: %s",
	)
	
	@AutoService(I18nDef::class)
	class OutType : I18nBase(
		Locale.ENGLISH to "Type: %s",
		Locale.SIMPLIFIED_CHINESE to "格式: %s",
	)
	
	@AutoService(I18nDef::class)
	class OutValue : I18nBase(
		Locale.ENGLISH to "Value: %s",
		Locale.SIMPLIFIED_CHINESE to "内容: %s",
	)
	
	@AutoService(I18nDef::class)
	class SettingNotFound : I18nBase(
		Locale.ENGLISH to "Setting %s not found, please verify the key",
		Locale.SIMPLIFIED_CHINESE to "未找到设置项 %s，请确认键正确",
	)
	
	@AutoService(I18nDef::class)
	class ShowSetting : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "以下内容将被重置为默认:",
	)
	
	@AutoService(I18nDef::class)
	class SureReset : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "输入 (y/yes) 确认:",
	)
	
	@AutoService(I18nDef::class)
	class SetTypeError : I18nBase(
		Locale.ENGLISH to "Type mismatch, please verify the entry type",
		Locale.SIMPLIFIED_CHINESE to "类型与原值不匹配，请确认条目类型",
	)
}
