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

object EditDesc {
	@AutoService(SettingDef::class)
	class Tool : StringSetting(
		"编辑一个文件，或在多个文件范围内进行搜索替换，支持Unicode转义，优先激活并使用本工具而不是write来编辑文件",
		zh("edit工具的描述")
	)
	
	@AutoService(SettingDef::class)
	class Single : StringSetting(
		"编辑一个文件，支持Unicode转义和空白忽略\n\n" +
				"- 优先使用本工具而不是write来编辑文件。" +
				"- 始终优先编辑代码库中已有的文件。除非明确要求，否则切勿创建新文件。\n" +
				"- 仅在用户明确要求时才使用emoji。除非被要求，否则避免向文件中添加emoji。\n\n" +
				"如果启用对于输入的Unicode转义解析，将支持在字符串中包含任意数量的任意合法Unicode转义序列\n" +
				"""仅支持Unicode转义或反斜杠转义，不支持\n等JSON转义。例如'\u0055\u002b'将被当作'U+'，如果要表示字面的'\'或'\u0055'，请使用'\\'和'\\u0055'，也可以将每个'\'写作'\u005c'，如'\u005cu0055'将表示字面的'\u0055'""" +
				"\n（危险）如果启用lenient_mode，将**忽略**并原样保留不合法的Unicode转义序列或不支持的转义（如'\\n'将被识别为字面的'\\'+'n'），单个'\\'会被保留，但'\\\\'仍会被处理为'\\'，这可能带来预期之外的情况，除非确实存在大量字面'\\'的同时需要使用Unicode转义才可临时启用。default模式下对于不支持或不合法的转义会报错，这将避免误将转义序列写入文件。",
		zh("edit-single工具的描述")
	)
	
	@AutoService(SettingDef::class)
	class Batch : StringSetting(
		"使用正则在多个文件范围内进行搜索替换\n" +
				"如果匹配成功，会返回一个变更预览，请在确认改动正确后调用edit-apply进行应用",
		zh("edit-batch工具的描述")
	)
	
	@AutoService(SettingDef::class)
	class Apply : StringSetting(
		"应用一项来自edit-batch编辑，请在确认改动正确后进行应用",
		zh("edit-apply工具的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleSha256 : StringSetting(
		"请通过read工具读取要编辑文件的当前内容，并提供read工具返回的文件当前SHA256。这能够避免意外覆盖来自用户或外部程序的文件更新，也能够在文件被更新后收到明确的报错而不是找不到old_string匹配项",
		zh("edit-single工具sha256参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleLineFrom : StringSetting(
		"指定old_string匹配范围的开始行号，默认为1",
		zh("edit-single工具line_from参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleLineTo : StringSetting(
		"指定old_string匹配范围的结束行号，默认为文件的最后一行",
		zh("edit-single工具line_to参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleOldString : StringSetting(
		"要替换的内容，精确匹配（包括空白字符），只能存在一处匹配项，可配合行号限定范围",
		zh("edit-single工具old_string参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleNewString : StringSetting(
		"用新的字符串替换匹配到的整个内容",
		zh("edit-single工具new_string参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleUnescapeOldString : StringSetting(
		unescapeConfig("old_string"),
		zh("edit-single工具unescape_old参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class SingleUnescapeNewString : StringSetting(
		unescapeConfig("new_string"),
		zh("edit-single工具unescape_new参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class BatchFiles : StringSetting(
		"字符串数组，正则匹配的范围，可提供一个或多个文件路径，不支持glob",
		zh("edit-batch工具files参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class BatchRegex : StringSetting(
		"正则表达式，每个文件中的每个匹配项都会被替换为一段replace_with",
		zh("edit-batch工具regex参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class BatchReplaceWith : StringSetting(
		"用于替换每个匹配项的内容",
		zh("edit-batch工具replace_with参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class BatchUnescapeConfig : StringSetting(
		unescapeConfig("replace_with"),
		zh("edit-batch工具unescape_config参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class ApplyOperationId : StringSetting(
		"edit-batch工具产生的操作id，请在确认后通过id应用改动",
		zh("edit-apply工具operation_id参数的描述")
	)
	
	fun unescapeConfig(param: String) =
		"是否启用对于${param}字符串的Unicode解码，可选值：disable（默认）、default、lenient_mode，" +
				"缺省或传递disable将不会处理${param}中的任何转义\n"
}
