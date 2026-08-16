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

package io.github.autotweaker.adapter.cli.commands.workspace

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object WorkspaceI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理工作区"),
	)
	
	@AutoService(I18nDef::class)
	class List : I18nBase(
		zh("列出所有工作区"),
	)
	
	@AutoService(I18nDef::class)
	class Create : I18nBase(
		zh("创建指定名称的工作区"),
	)
	
	@AutoService(I18nDef::class)
	class Rename : I18nBase(
		zh("重命名指定名称的工作区"),
	)
	
	@AutoService(I18nDef::class)
	class Delete : I18nBase(
		zh("删除指定名称的工作区"),
	)
	
	@AutoService(I18nDef::class)
	class Directory : I18nBase(
		zh("工作区目录，不指定则使用当前目录"),
	)
	
	@AutoService(I18nDef::class)
	class SkipConfirm : I18nBase(
		zh("跳过删除确认"),
	)
	
	@AutoService(I18nDef::class)
	class NewName : I18nBase(
		zh("新的工作区名称"),
	)
	
	@AutoService(I18nDef::class)
	class InvalidPath : I18nBase(
		zh("无效的工作区路径"),
	)
	
	@AutoService(I18nDef::class)
	class NotFound : I18nBase(
		zh("找不到工作区 '%s'"),
	)
	
	@AutoService(I18nDef::class)
	class Name : I18nBase(
		zh("名称: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Path : I18nBase(
		zh("路径: %s"),
	)
	
	@AutoService(I18nDef::class)
	class Confirm : I18nBase(
		zh("即将删除工作区 %s ('%s')，输入 (y/yes) 确认:"),
	)
	
	@AutoService(I18nDef::class)
	class ListFormat : I18nBase(
		zh("%s - %s (%s 个会话)"),
	)
}
