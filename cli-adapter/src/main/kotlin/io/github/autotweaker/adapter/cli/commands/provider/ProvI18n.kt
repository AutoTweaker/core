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

object ProvI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		en("Query and manage LLM providers"),
		zh("查询和管理模型提供商"),
	)
	
	@AutoService(I18nDef::class)
	class List : I18nBase(
		en("List configured providers"),
		zh("列出已配置的提供商"),
	)
	
	@AutoService(I18nDef::class)
	class Show : I18nBase(
		en("Show detailed info of specified provider"),
		zh("显示指定提供商的详细信息"),
	)
	
	@AutoService(I18nDef::class)
	class Types : I18nBase(
		en("List available provider types"),
		zh("列出可用的提供商类型"),
	)
	
	@AutoService(I18nDef::class)
	class Info : I18nBase(
		en("Show metadata for a provider type"),
		zh("查看指定提供商类型的元数据"),
	)
	
	@AutoService(I18nDef::class)
	class Add : I18nBase(
		en("Add a provider"),
		zh("添加提供商"),
	)
	
	@AutoService(I18nDef::class)
	class AddName : I18nBase(
		en("Name for the new provider"),
		zh("新提供商的名称"),
	)
	
	@AutoService(I18nDef::class)
	class AddType : I18nBase(
		en("Type of the new provider"),
		zh("新提供商的类型"),
	)
	
	@AutoService(I18nDef::class)
	class AddKey : I18nBase(
		en("Key name for the new provider"),
		zh("新提供商的密钥名称"),
	)
	
	@AutoService(I18nDef::class)
	class AddUrl : I18nBase(
		en("API URL for the new provider"),
		zh("新提供商的API端点"),
	)
	
	@AutoService(I18nDef::class)
	class Remove : I18nBase(
		en("Remove specified provider"),
		zh("删除指定提供商"),
	)
	
	@AutoService(I18nDef::class)
	class Yes : I18nBase(
		en("Skip interactive delete confirmation"),
		zh("跳过交互式的删除确认"),
	)
	
	@AutoService(I18nDef::class)
	class Rename : I18nBase(
		en("Rename a provider"),
		zh("重命名指定提供商"),
	)
	
	@AutoService(I18nDef::class)
	class NewName : I18nBase(
		zh("新的提供商名称"),
	)
	
	@AutoService(I18nDef::class)
	class Update : I18nBase(
		en("Edit a provider"),
		zh("编辑指定提供商"),
	)
	
	@AutoService(I18nDef::class)
	class ProviderNotFound : I18nBase(
		en("Provider not found: %s"),
		zh("未找到提供商: %s"),
	)
}
