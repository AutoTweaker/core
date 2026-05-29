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
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

internal object CfgI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Query and manage settings"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "查询和管理应用设置"),
		)
	}
	
	@AutoService(I18nDef::class)
	class List : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "List all settings"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出所有设置条目"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Full : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Show full setting details"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "显示设置条目的完整信息"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Limit : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Limit number of entries"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "仅显示指定数量的条目"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Set : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Modify a setting by key"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "根据键修改指定设置项"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SetValue : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "New value for the setting"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "设置项的新值"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Search : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Search settings"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "搜索设置条目"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SearchKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Search in setting keys"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "在设置键中搜索"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SearchValue : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Search in setting values"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "在设置值中搜索"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SearchDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Search in setting descriptions"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "在设置描述中搜索"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Key: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "键名: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutDesc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Description: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "描述: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Type: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "格式: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class OutValue : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Value: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "内容: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SetNotFound : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Setting %s not found, please verify the key"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "未找到设置项 %s，请确认键正确"),
		)
	}
	
	@AutoService(I18nDef::class)
	class SetTypeError : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Type mismatch, please verify the entry type"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "类型与原值不匹配，请确认条目类型"),
		)
	}
}
