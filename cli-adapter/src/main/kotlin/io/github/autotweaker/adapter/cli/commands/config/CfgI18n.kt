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
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object CfgI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		en("Query and manage settings"),
		zh("查询和管理应用设置"),
	)
	
	@AutoService(I18nDef::class)
	class List : I18nBase(
		en("List all settings"),
		zh("列出所有设置条目"),
	)
	
	@AutoService(I18nDef::class)
	class Full : I18nBase(
		en("Show full setting details"),
		zh("显示设置条目的完整信息"),
	)
	
	@AutoService(I18nDef::class)
	class Limit : I18nBase(
		en("Limit number of entries"),
		zh("仅显示指定数量的条目"),
	)
	
	@AutoService(I18nDef::class)
	class Set : I18nBase(
		en("Modify a setting by key"),
		zh("根据键修改指定设置项"),
	)
	
	@AutoService(I18nDef::class)
	class SetValue : I18nBase(
		en("New value for the setting"),
		zh("设置项的新值"),
	)
	
	@AutoService(I18nDef::class)
	class Search : I18nBase(
		zh("搜索设置条目，默认在 id 和值搜索"),
	)
	
	@AutoService(I18nDef::class)
	class SearchKey : I18nBase(
		en("Search in setting keys"),
		zh("在设置 id 中搜索"),
	)
	
	@AutoService(I18nDef::class)
	class SearchValue : I18nBase(
		en("Search in setting values"),
		zh("在设置值中搜索"),
	)
	
	@AutoService(I18nDef::class)
	class SearchDesc : I18nBase(
		zh("在当前语言对应的设置描述中搜索"),
	)
	
	@AutoService(I18nDef::class)
	class Reset : I18nBase(
		zh("重置指定的设置项"),
	)
	
	@AutoService(I18nDef::class)
	class Yes : I18nBase(
		zh("跳过重置确认"),
	)
	
	@AutoService(I18nDef::class)
	class OutKey : I18nBase(
		en("Key: %s"),
		zh("键名: %s"),
	)
	
	@AutoService(I18nDef::class)
	class OutDesc : I18nBase(
		en("Description: %s"),
		zh("描述: %s"),
	)
	
	@AutoService(I18nDef::class)
	class OutType : I18nBase(
		en("Type: %s"),
		zh("格式: %s"),
	)
	
	@AutoService(I18nDef::class)
	class OutValue : I18nBase(
		en("Value: %s"),
		zh("内容: %s"),
	)
	
	@AutoService(I18nDef::class)
	class SettingNotFound : I18nBase(
		en("Setting %s not found, please verify the key"),
		zh("未找到设置项 %s，请确认键正确"),
	)
	
	@AutoService(I18nDef::class)
	class ShowSetting : I18nBase(
		zh("以下内容将被重置为默认:"),
	)
	
	@AutoService(I18nDef::class)
	class SureReset : I18nBase(
		zh("输入 (y/yes) 确认:"),
	)
	
	@AutoService(I18nDef::class)
	class SetTypeError : I18nBase(
		en("Type mismatch, please verify the entry type"),
		zh("类型与原值不匹配，请确认条目类型"),
	)
}
