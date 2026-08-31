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

package io.github.autotweaker.core.domain.tool.impl.edit

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object EditMessage {
	@AutoService(SettingDef::class)
	class InvalidHash : StringSetting(
		"无效的哈希：%s",
		zh("edit工具sha256参数非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class ReadFailed : StringSetting(
		"读取目标文件时出错：%s",
		zh("edit工具读取目标文件失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class HashMismatch : StringSetting(
		"编辑文件失败，SHA256不匹配，文件已被外部更新，请重新读取文件",
		zh("edit工具SHA256校验失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class LineFromInvalid : StringSetting(
		"line_from必须大于或等于1",
		zh("edit工具line_from小于1时的描述")
	)
	
	@AutoService(SettingDef::class)
	class LineToInvalid : StringSetting(
		"line_to不能小于line_from",
		zh("edit工具line_to小于line_from时的描述")
	)
	
	@AutoService(SettingDef::class)
	class InvalidEscape : StringSetting(
		"%s中包含非法或未知的转义序列：%s",
		zh("edit工具字符串转义非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class OldStringEmpty : StringSetting(
		"old_string不能为空",
		zh("edit工具old_string为空时的描述")
	)
	
	@AutoService(SettingDef::class)
	class NoMatch : StringSetting(
		"指定的范围内没有old_string的匹配项。请重新读取文件确认当前状态符合预期，并确保提供的字符精确",
		zh("edit工具未找到old_string匹配项时的描述")
	)
	
	@AutoService(SettingDef::class)
	class NotUnique : StringSetting(
		"指定的范围内存在多处old_string的匹配项，请尝试缩小行区间或在old_string中提供更多上下文",
		zh("edit工具存在多处匹配项时的描述")
	)
	
	@AutoService(SettingDef::class)
	class Updated : StringSetting(
		"已更新文件 %s：\n%s",
		zh("edit工具更新文件成功时的响应")
	)
	
	@AutoService(SettingDef::class)
	class Unchanged : StringSetting(
		"UNCHANGED",
		zh("edit工具文件内容无变化时的diff占位文本")
	)
}
