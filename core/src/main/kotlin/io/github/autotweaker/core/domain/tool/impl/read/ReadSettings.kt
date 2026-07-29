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
	class SummarizePromptSetting : StringSetting(
		"你是文件总结助手，请根据用户输入和以下指令生成关于文件内容的摘要", zh(
			"summarize功能使用的系统提示词，这段文本被安置在llm自定义指令之前"
		)
	)
	
	@AutoService(SettingDef::class)
	class DescriptionSetting : StringSetting(
		"读取一个文件，支持获取摘要以及Unicode代码", zh(
			"read工具的描述，在read工具未激活时展示给llm"
		)
	)
	
	@AutoService(SettingDef::class)
	class FileFuncDescriptionSetting : StringSetting(
		"读取一个文件，最大字符数%s，最大行数%s，返回内容的第一行为文件内容的SHA256，第二行开始是文件内容，注意区分。\n请注意：始终使用此工具而不是bash来获取文件内容",
		zh(
			"read_file工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeFuncDescriptionSetting : StringSetting(
		"获取一个文件的摘要，最大字符数%s，最小字符数%s，最大行数%s，在文件较大时使用此工具很合适", zh(
			"read_summarize工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class StartLinePropDescriptionSetting : StringSetting(
		"读取文件的开始行号，从1开始", zh(
			"read工具start_line参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class EndLinePropDescriptionSetting : StringSetting(
		"读取文件的结束行号，不能小于开始行号，可以大于文件总行数", zh(
			"read工具end_line参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class LineNumberPropDescriptionSetting : StringSetting(
		"是否启用行号，默认为true，启用行号后会在每行的开头添加[行号][制表符]作为前缀，注意区分", zh(
			"read_summarize工具line_number参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class UnicodeEscapePropDescriptionSetting : StringSetting(
		"将文件内容进行Unicode转义，用于在文件内容看起来无效或需要确认准确字符时启用，默认为false", zh(
			"read-file工具unicode_escape参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizePromptPropDescriptionSetting : StringSetting(
		"用于总结文件的提示词，调整此字段来要求总结器关注不同细节", zh(
			"read_summarize工具prompt参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageTooManyLinesSetting : StringSetting(
		"读取的行数过多，上限为%s", zh(
			"read工具读取过多内容时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageFileNotFoundSetting : StringSetting(
		"文件不存在或访问被拒绝", zh(
			"read工具读取不存在的文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageFileCannotReadSetting : StringSetting(
		"请求路径是一个目录、文件是一个二进制文件、文件所使用的编码不支持或文件已损坏", zh(
			"read工具读取的文件无法解析时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessagePathOutsideWorkspaceSetting : StringSetting(
		"请求的文件路径在工作目录外部", zh(
			"read工具在容器内读取工作目录外的文件时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartLineErrorSetting : StringSetting(
		"start_line必须大于或等于1", zh(
			"read工具start_line不合法的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartLineBiggerThanEndSetting : StringSetting(
		"start_line不能大于end_line", zh(
			"read工具start_line大于end_line的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class FileMessageDuplicateSetting : StringSetting(
		"读取的文件内容与文件哈希%s时的读取相同", zh(
			"read_file工具读取重复内容时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class MessageStartCharErrorSetting : StringSetting(
		"start_char必须大于或等于0", zh(
			"read_unicode工具start_char不合法的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMessageTooFewSetting : StringSetting(
		"用于总结的字符数过少（%s），必须大于%s", zh(
			"read_summarize工具总结内容过少时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMessageFailedSetting : StringSetting(
		"总结器出错，请及时告知用户：%s", zh(
			"read_summarize总结llm出错时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class FileMessageTruncateSetting : StringSetting(
		"[字符数过多，后续内容已被截断（共%s字符），请尝试使用read_summarize工具]", zh(
			"read_file工具截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMessageOutputTruncateSetting : StringSetting(
		"[总结器输出内容过多，后续内容已被截断（共%s字符），请尝试修改总结器提示词]", zh(
			"read_summarize工具截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMessageInputTruncateSetting : StringSetting(
		"[字符数过多，后续内容已被截断（共%s字符）]", zh(
			"read_summarize工具总结器输入内容截断位置的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class FileMaxLinesSetting : IntSetting(
		500, zh(
			"read_file工具最大允许行数"
		)
	)
	
	@AutoService(SettingDef::class)
	class FileMaxCharsSetting : IntSetting(
		20000, zh(
			"read_file工具最大允许字符数，超出会截断"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxLinesSetting : IntSetting(
		5000, zh(
			"read_summarize工具最大允许行数"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxInputCharsSetting : IntSetting(
		200000, zh(
			"read_summarize工具最大输入字符数，超出会截断"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMinCharsSetting : IntSetting(
		500, zh(
			"read_summarize工具最小允许字符数，小于此会返回错误消息"
		)
	)
	
	@AutoService(SettingDef::class)
	class SummarizeMaxOutputCharsSetting : IntSetting(
		5000, zh(
			"read_summarize工具最大输出字符数，超出会截断"
		)
	)
}
