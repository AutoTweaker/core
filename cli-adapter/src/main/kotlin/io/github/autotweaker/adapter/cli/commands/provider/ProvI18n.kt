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

package io.github.autotweaker.adapter.cli.commands.provider

import com.google.auto.service.AutoService
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.i18n.LocalizedString
import java.util.*

object ProvI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Query and manage LLM providers"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "查询和管理模型提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class List : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "List configured providers"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出已配置的提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Show : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Show detailed info of specified provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "显示指定提供商的详细信息"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Types : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "List available provider types"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "列出可用的提供商类型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Info : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Show metadata for a provider type"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "查看指定提供商类型的元数据"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Add : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Add a provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "添加提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class AddName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Name for the new provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新提供商的名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class AddType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Type of the new provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新提供商的类型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class AddKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Key name for the new provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新提供商的密钥名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class AddUrl : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "API URL for the new provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新提供商的API端点"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Remove : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Remove specified provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "删除指定提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Yes : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Skip interactive delete confirmation"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "跳过交互式的删除确认"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Rename : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Rename a provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "重命名指定提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class NewName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "新的提供商名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class Update : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Edit a provider"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "编辑指定提供商"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ProviderNotFound : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Provider not found: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "未找到提供商: %s"),
		)
	}
}
