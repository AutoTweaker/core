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

object ProvCommandsI18n {
	@AutoService(I18nDef::class)
	class MissingName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Please provide the provider name"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请提供提供商名称"),
		)
	}
	
	@AutoService(I18nDef::class)
	class MissingType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Please provide the provider type"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请提供提供商类型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class MissingKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Please provide the key"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "请提供密钥"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Invalid provider type"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "无效的提供商类型"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "The provided key does not exist"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "提供的密钥不存在"),
		)
	}
	
	@AutoService(I18nDef::class)
	class InvalidUrl : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "URL parse failed: %s"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "URL解析失败: %s"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptName : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Provider name:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "提供商名称:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptType : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Provider type:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "提供商类型:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptKey : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Key name:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "密钥名称:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class PromptUrl : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "API URL (leave blank for default):"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "API端点 (留空默认):"),
		)
	}
	
	@AutoService(I18nDef::class)
	class RemoveListCount : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "About to delete %s providers:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "即将删除 %s 个提供商:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class RemoveConfirm : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.ENGLISH, "Enter (y/yes) to confirm:"),
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "输入 (y/yes) 确认删除:"),
		)
	}
	
	@AutoService(I18nDef::class)
	class ProviderExistsError : I18nDef {
		override val localizations = listOf(
			LocalizedString(Locale.SIMPLIFIED_CHINESE, "名为 %s 的提供商已存在"),
		)
	}
}
