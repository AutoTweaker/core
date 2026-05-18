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

package io.github.autotweaker.core.tool.impl.read

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue

object ReadSettings {
	@AutoService(SettingDef::class)
	object SummarizePromptSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("你是文件总结助手，请根据用户输入和以下指令生成关于文件内容的摘要")
		override val description = "summarize功能使用的系统提示词，这段文本被安置在llm自定义指令之前"
	}
	
	@AutoService(SettingDef::class)
	object DescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取一个文件，支持获取摘要以及Unicode代码")
		override val description = "read工具的描述，在read工具未激活时展示给llm"
	}
	
	@AutoService(SettingDef::class)
	object FileFuncDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("读取一个文件，最大字符数%s，最大行数%s，返回内容的第一行为文件内容的SHA256，第二行开始是文件内容，注意区分")
		override val description = "read_file工具的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeFuncDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("获取一个文件的摘要，最大字符数%s，最小字符数%s，最大行数%s，在文件较大时使用此工具很合适")
		override val description = "read_summarize工具的描述"
	}
	
	@AutoService(SettingDef::class)
	object UnicodeFuncDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("读取一个文件但是返回每个字符的Unicode编码，在普通读取看起来没有返回有效内容时使用")
		override val description = "read_unicode工具的描述"
	}
	
	@AutoService(SettingDef::class)
	object FilePathPropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("文件的路径，支持绝对路径或相对路径")
		override val description = "read工具file_path参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object StartLinePropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取文件的开始行号，从1开始")
		override val description = "read工具start_line参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object EndLinePropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取文件的结束行号，不能小于开始行号，可以大于文件总行数")
		override val description = "read工具end_line参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object LineNumberPropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("是否启用行号，默认为true，启用行号后会在每行的开头添加[行号][制表符]作为前缀，注意区分")
		override val description = "read_summarize工具line_number参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizePromptPropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("用于总结文件的提示词，调整此字段来要求总结器关注不同细节")
		override val description = "read_summarize工具prompt参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object UnicodeMaxCharsPropDescriptionSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取文件的前n个字符，这将包括换行符等特殊字符，最多%s个字符")
		override val description = "read_unicode工具max_chars参数的描述"
	}
	
	@AutoService(SettingDef::class)
	object MessageTooManyLinesSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取的行数过多，上限为%s")
		override val description = "read工具读取过多内容时的描述"
	}
	
	@AutoService(SettingDef::class)
	object MessageFileNotFoundSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("文件不存在或访问被拒绝")
		override val description = "read工具读取不存在的文件时的描述"
	}
	
	@AutoService(SettingDef::class)
	object MessageFileCannotReadSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("文件是一个二进制文件、文件所使用的编码不支持或文件已损坏")
		override val description = "read工具读取的文件无法解析时的描述"
	}
	
	@AutoService(SettingDef::class)
	object MessageStartLineErrorSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("start_line必须大于或等于1")
		override val description = "read工具start_line不合法的描述"
	}
	
	@AutoService(SettingDef::class)
	object MessageStartLineBiggerThanEndSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("start_line不能大于end_line")
		override val description = "read工具start_line大于end_line的描述"
	}
	
	@AutoService(SettingDef::class)
	object FileMessageDuplicateSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取的文件内容与文件哈希%s时的读取相同")
		override val description = "read_file工具读取重复内容时的描述"
	}
	
	@AutoService(SettingDef::class)
	object UnicodeMessageTooManyCharsSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("读取的字符数过多，上限为%s")
		override val description = "read_unicode工具读取过多内容时的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMessageTooFewSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("用于总结的字符数过少（%s），必须大于%s")
		override val description = "read_summarize工具总结内容过少时的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMessageFailedSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("总结器出错，请及时告知用户：%s")
		override val description = "read_summarize总结llm出错时的描述"
	}
	
	@AutoService(SettingDef::class)
	object FileMessageTruncateSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("<字符数过多，后续内容已被截断（共%s字符），请尝试使用read_summarize工具>")
		override val description = "read_file工具截断位置的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMessageOutputTruncateSetting : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("<总结器输出内容过多，后续内容已被截断（共%s字符），请尝试修改总结器提示词>")
		override val description = "read_summarize工具截断位置的描述"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMessageInputTruncateSetting : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("<字符数过多，后续内容已被截断（共%s字符）>")
		override val description = "read_summarize工具总结器输入内容截断位置的描述"
	}
	
	@AutoService(SettingDef::class)
	object FileMaxLinesSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(500)
		override val description = "read_file工具最大允许行数"
	}
	
	@AutoService(SettingDef::class)
	object FileMaxCharsSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(20000)
		override val description = "read_file工具最大允许字符数，超出会截断"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMaxLinesSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(5000)
		override val description = "read_summarize工具最大允许行数"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMaxInputCharsSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(200000)
		override val description = "read_summarize工具最大输入字符数，超出会截断"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMinCharsSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(500)
		override val description = "read_summarize工具最小允许字符数，小于此会返回错误消息"
	}
	
	@AutoService(SettingDef::class)
	object SummarizeMaxOutputCharsSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(5000)
		override val description = "read_summarize工具最大输出字符数，超出会截断"
	}
	
	@AutoService(SettingDef::class)
	object UnicodeMaxCharsSetting : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(500)
		override val description = "read_unicode工具最大允许字符数，超出会返回错误消息"
	}
}
