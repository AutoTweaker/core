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

package io.github.autotweaker.core.domain.tool.impl

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object ToolSettings {
	@AutoService(SettingDef::class)
	class PathErrorMessage : StringSetting(
		"提供的路径不合法，请检查提供的路径参数",
		zh("路径解析失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class FilePathDesc : StringSetting(
		"文件的路径，请使用基于工作区路径的相对路径。\n" +
				"除非需要访问工作区外部，否则请不要使用绝对路径，这可能导致意外的越权访问",
		zh("工具文件路径参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class InvalidHash : StringSetting(
		"必须提供至少8位的哈希值，你提供的 '%s' 只有 %s 位",
		zh("工具sha256参数非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class UseShortHash : StringSetting(
		"你提供了%s位的哈希字符串，实际上你只需要提供完整哈希的前8位来避免复制错误",
		zh("文件sha256不匹配且提供了超过8位哈希的附加描述")
	)
}
