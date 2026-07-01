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
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.i18n.I18nDef
import java.util.*

object ProvCommandsI18n {
	@AutoService(I18nDef::class)
	class MissingName : I18nBase(
		Locale.ENGLISH to "Please provide the provider name",
		Locale.SIMPLIFIED_CHINESE to "请提供提供商名称",
	)
	
	@AutoService(I18nDef::class)
	class MissingType : I18nBase(
		Locale.ENGLISH to "Please provide the provider type",
		Locale.SIMPLIFIED_CHINESE to "请提供提供商类型",
	)
	
	@AutoService(I18nDef::class)
	class MissingKey : I18nBase(
		Locale.ENGLISH to "Please provide the key",
		Locale.SIMPLIFIED_CHINESE to "请提供密钥",
	)
	
	@AutoService(I18nDef::class)
	class InvalidType : I18nBase(
		Locale.ENGLISH to "Invalid provider type",
		Locale.SIMPLIFIED_CHINESE to "无效的提供商类型",
	)
	
	@AutoService(I18nDef::class)
	class InvalidKey : I18nBase(
		Locale.ENGLISH to "The provided key does not exist",
		Locale.SIMPLIFIED_CHINESE to "提供的密钥不存在",
	)
	
	@AutoService(I18nDef::class)
	class InvalidUrl : I18nBase(
		Locale.ENGLISH to "URL parse failed: %s",
		Locale.SIMPLIFIED_CHINESE to "URL解析失败: %s",
	)
	
	@AutoService(I18nDef::class)
	class PromptName : I18nBase(
		Locale.ENGLISH to "Provider name:",
		Locale.SIMPLIFIED_CHINESE to "提供商名称:",
	)
	
	@AutoService(I18nDef::class)
	class PromptType : I18nBase(
		Locale.ENGLISH to "Provider type:",
		Locale.SIMPLIFIED_CHINESE to "提供商类型:",
	)
	
	@AutoService(I18nDef::class)
	class PromptKey : I18nBase(
		Locale.ENGLISH to "Key name:",
		Locale.SIMPLIFIED_CHINESE to "密钥名称:",
	)
	
	@AutoService(I18nDef::class)
	class PromptUrl : I18nBase(
		Locale.ENGLISH to "API URL (leave blank for default):",
		Locale.SIMPLIFIED_CHINESE to "API端点 (留空默认):",
	)
	
	@AutoService(I18nDef::class)
	class RemoveListCount : I18nBase(
		Locale.ENGLISH to "About to delete %s providers:",
		Locale.SIMPLIFIED_CHINESE to "即将删除 %s 个提供商:",
	)
	
	@AutoService(I18nDef::class)
	class RemoveConfirm : I18nBase(
		Locale.ENGLISH to "Enter (y/yes) to confirm:",
		Locale.SIMPLIFIED_CHINESE to "输入 (y/yes) 确认删除:",
	)
	
	@AutoService(I18nDef::class)
	class ProviderExistsError : I18nBase(
		Locale.SIMPLIFIED_CHINESE to "名为 %s 的提供商已存在",
	)
}
