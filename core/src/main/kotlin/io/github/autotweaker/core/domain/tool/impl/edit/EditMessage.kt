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
	class FileTooLarge : StringSetting(
		"目标文件可能超出了10MB，程序无法完整读取，也无法计算更新后的完整内容",
		zh("edit工具目标文件过大时的描述")
	)
	
	@AutoService(SettingDef::class)
	class HashMismatch : StringSetting(
		"编辑文件失败，SHA256不匹配，文件已被外部更新，请重新读取文件",
		zh("edit工具SHA256校验失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class HasInvalidReplacement : StringSetting(
		"以下%s个编辑段存在错误，文件没有被更新：",
		zh("edit工具编辑段校验失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class ReplacementInvalid : StringSetting(
		"编辑段%s存在错误：",
		zh("edit工具编辑段校验失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class ReplacementDuplicate : StringSetting(
		"编辑段%s与%s重叠",
		zh("edit工具编辑段重叠的描述")
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
	class LineToOutOfFile : StringSetting(
		"line_to超出了文件总行数（%s行）",
		zh("edit工具line_to超出文件的描述")
	)
	
	@AutoService(SettingDef::class)
	class InvalidEscape : StringSetting(
		"%s中包含非法或未知的转义序列：%s",
		zh("edit工具字符串转义非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class EditsEmpty : StringSetting(
		"edits不能为空",
		zh("edit工具edits数组为空时的描述")
	)
	
	@AutoService(SettingDef::class)
	class OldStringEmpty : StringSetting(
		"old_string不能为空",
		zh("edit工具old_string为空时的描述")
	)
	
	@AutoService(SettingDef::class)
	class NoMatch : StringSetting(
		"以下指定的范围内没有old_string的匹配项。\n" +
				"%s\n" +
				"请重新读取文件确认当前状态符合预期，并确保提供的字符精确。你可以使用read工具的Unicode转义模式获取指定片段的精确内容",
		zh("edit工具未找到old_string匹配项时的描述")
	)
	
	@AutoService(SettingDef::class)
	class NotUnique : StringSetting(
		"以下指定的范围内存在多处old_string的匹配项。\n" +
				"%s\n" +
				"请尝试缩小行区间或在old_string中提供更多上下文",
		zh("edit工具存在多处匹配项时的描述")
	)
	
	@AutoService(SettingDef::class)
	class Updated : StringSetting(
		"已更新文件 %s，当前 SHA256：%s，文件变更：\n%s",
		zh("edit工具更新文件成功时的响应")
	)
	
	@AutoService(SettingDef::class)
	class Unchanged : StringSetting(
		"UNCHANGED",
		zh("edit工具文件内容无变化时的diff占位文本")
	)
}
