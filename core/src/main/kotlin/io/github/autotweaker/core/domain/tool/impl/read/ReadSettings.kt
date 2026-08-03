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

package io.github.autotweaker.core.domain.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object ReadSettings {
	@AutoService(SettingDef::class)
	class ToolDescription : StringSetting(
		"读取一个文件，支持获取摘要以及Unicode代码", zh(
			"read工具的描述，在read工具未激活时展示给llm"
		)
	)
	
	@AutoService(SettingDef::class)
	class ReadFileDescription : StringSetting(
		"读取一个文件，最大字符数%s，最大行数%s，返回内容的第一行为文件内容的SHA256，第二行开始是文件内容，注意区分。\n请注意：始终使用此工具而不是bash来获取文件内容",
		zh(
			"read-file工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class ReadSummarizeDescription : StringSetting(
		"获取一个文件的摘要，最大字符数%s，最小字符数%s，最大行数%s，在文件较大时使用此工具很合适", zh(
			"read-summarize工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class StartLineDesc : StringSetting(
		"读取文件的开始行号，从1开始", zh(
			"read工具start_line参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class EndLineDesc : StringSetting(
		"读取文件的结束行号，不能小于开始行号，可以大于文件总行数", zh(
			"read工具end_line参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class LineNumberDesc : StringSetting(
		"是否启用行号，默认为true，启用行号后会在每行的开头添加[行号][制表符]作为前缀，注意区分", zh(
			"read-file工具line_number参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class UnicodeEscapeDesc : StringSetting(
		"将文件内容进行Unicode转义，用于在文件内容看起来无效或需要确认准确字符时启用，默认为false", zh(
			"read-file工具unicode_escape参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizePromptDesc : StringSetting(
		"用于总结文件的提示词，调整此字段来要求总结器关注不同细节", zh(
			"read-summarize工具prompt参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageTooManyLines : StringSetting(
		"读取的行数过多，上限为%s", zh(
			"read工具读取过多内容时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageFileNotFound : StringSetting(
		"文件%s不存在", zh(
			"read工具读取不存在的文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageNotRegularFile : StringSetting(
		"文件%s不是一个可读取的普通文件", zh(
			"read工具读取文件不是一个普通文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageFileAccessDenied : StringSetting(
		"当前用户没有权限读取这个文件", zh(
			"read工具没有权限取文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageFileCannotRead : StringSetting(
		"读取文件%s时失败：%s", zh(
			"read工具读取文件出错时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessagePathOutsideWorkspace : StringSetting(
		"请求的文件路径在工作目录外部", zh(
			"read工具在容器内读取工作目录外的文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartLineError : StringSetting(
		"start_line必须大于或等于1", zh(
			"read工具start_line不合法的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartLineBiggerThanEnd : StringSetting(
		"start_line不能大于end_line", zh(
			"read工具start_line大于end_line的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartLineBiggerThanFile : StringSetting(
		"start_line超出了文件可读行数（%s）", zh(
			"read工具start_line大于文件总行数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class DuplicateMessage : StringSetting(
		"读取的文件内容与文件哈希%s时的读取相同", zh(
			"read-file工具读取重复内容时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageTooFew : StringSetting(
		"用于总结的字符数过少（%s），必须大于%s", zh(
			"read-summarize工具总结内容过少时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageSummarizeFailed : StringSetting(
		"总结器出错，请及时告知用户：%s", zh(
			"read-summarize总结llm出错时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizePrompt : StringSetting(
		"你是文件总结助手，请根据用户输入和以下指令生成关于文件内容的摘要", zh(
			"summarize功能使用的系统提示词，这段文本被安置在llm自定义指令之前"
		)
	)
	
	@AutoService(SettingDef::class)
	class TruncateMessage : StringSetting(
		"[字符数过多，后续内容已被截断（共%s字符），请尝试使用read-summarize工具]", zh(
			"read-file工具截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeOutputTruncationMessage : StringSetting(
		"[总结器输出内容过多，后续内容已被截断（共%s字符），请尝试修改总结器提示词]", zh(
			"read-summarize工具截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeInputTruncationMessage : StringSetting(
		"[字符数过多，后续内容已被截断（共%s字符）]", zh(
			"read-summarize工具总结器输入内容截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxReadLines : IntSetting(
		500, zh(
			"read-file工具最大允许行数"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxReadChars : IntSetting(
		20000, zh(
			"read-file工具最大允许字符数，超出会截断"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxLines : IntSetting(
		5000, zh(
			"read-summarize工具最大允许行数"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxInputChars : IntSetting(
		200000, zh(
			"read-summarize工具最大输入字符数，超出会截断"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMinChars : IntSetting(
		500, zh(
			"read-summarize工具最小允许字符数，小于此会返回错误消息"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxOutputChars : IntSetting(
		5000, zh(
			"read-summarize工具最大输出字符数，超出会截断"
		)
	)
}
