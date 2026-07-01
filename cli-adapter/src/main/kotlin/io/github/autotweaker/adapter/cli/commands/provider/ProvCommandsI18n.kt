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
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object ProvCommandsI18n {
	@AutoService(I18nDef::class)
	class MissingName : I18nBase(
		en("Please provide the provider name"),
		zh("请提供提供商名称"),
	)
	
	@AutoService(I18nDef::class)
	class MissingType : I18nBase(
		en("Please provide the provider type"),
		zh("请提供提供商类型"),
	)
	
	@AutoService(I18nDef::class)
	class MissingKey : I18nBase(
		en("Please provide the key"),
		zh("请提供密钥"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidType : I18nBase(
		en("Invalid provider type"),
		zh("无效的提供商类型"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidKey : I18nBase(
		en("The provided key does not exist"),
		zh("提供的密钥不存在"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidUrl : I18nBase(
		en("URL parse failed: %s"),
		zh("URL解析失败: %s"),
	)
	
	@AutoService(I18nDef::class)
	class PromptName : I18nBase(
		en("Provider name:"),
		zh("提供商名称:"),
	)
	
	@AutoService(I18nDef::class)
	class PromptType : I18nBase(
		en("Provider type:"),
		zh("提供商类型:"),
	)
	
	@AutoService(I18nDef::class)
	class PromptKey : I18nBase(
		en("Key name:"),
		zh("密钥名称:"),
	)
	
	@AutoService(I18nDef::class)
	class PromptUrl : I18nBase(
		en("API URL (leave blank for default):"),
		zh("API端点 (留空默认):"),
	)
	
	@AutoService(I18nDef::class)
	class RemoveListCount : I18nBase(
		en("About to delete %s providers:"),
		zh("即将删除 %s 个提供商:"),
	)
	
	@AutoService(I18nDef::class)
	class RemoveConfirm : I18nBase(
		en("Enter (y/yes) to confirm:"),
		zh("输入 (y/yes) 确认删除:"),
	)
	
	@AutoService(I18nDef::class)
	class ProviderExistsError : I18nBase(
		zh("名为 %s 的提供商已存在"),
	)
}
