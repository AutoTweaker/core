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

package io.github.autotweaker.core.domain.tool.impl.write

import com.google.auto.service.AutoService
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object WriteDesc {
	@AutoService(SettingDef::class)
	class Tool : StringSetting(
		"创建一个新文件，或覆写一个已有文件，支持Unicode转义。优先使用edit来更新文件的部分片段。\n不要使用bash来创建文件，而是激活此工具来写入文件，即使要写入特殊字符。",
		zh("write工具的未激活描述")
	)
	
	@AutoService(SettingDef::class)
	class Function : StringSetting(
		"创建一个新文件，或覆写已有文件，你需要确保提前通过read工具读取目标文件。\n" +
				"支持unicode转义，不支持\\n等json转义，需要通过unescape_unicode显式启用。\n" +
				"你应该优先使用edit来更新文件的部分片段。\n" +
				"始终避免在工作区中创建临时文件或任务报告类文件，请在'/tmp/$APP_NAME_LOWERCASE'下创建这类文件。\n" +
				"file_path的父目录若不存在会自动创建，如果你已经确认了要写入的位置，无需提前创建目录或检查父目录的存在性。",
		zh("write工具的描述")
	)
	
	@AutoService(SettingDef::class)
	class Sha256 : StringSetting(
		"如果目标文件已存在，请通过read工具读取文件的完整内容，并提供read工具返回的文件当前SHA256，这能够避免意外覆盖来自用户或外部程序的文件更新",
		zh("write工具sha256参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class Content : StringSetting(
		"要写入到文件的新内容，如果启用unescape_unicode，可以包含若干Unicode转义序列，普通字符会按原样解析",
		zh("write工具content参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class UnescapeUnicode : StringSetting(
		"是否对content中的Unicode转义序列进行解码，默认false。仅支持Unicode转义以及反斜杠转义，例如\\u0055将被解析为'U'，\\\\u0055将被解析为'\\u0055'，不支持JSON转义如\\n或\\t",
		zh("write工具unescape_unicode参数的描述")
	)
	
	@AutoService(SettingDef::class)
	class LenientUnescape : StringSetting(
		"若启用unescape_unicode，将原样保留content中不合法或不支持的转义，通常不应当启用，这将导致解析行为难以预测\n" +
				"仅在要写入到内容确实包含大量字面的反斜杠或JSON转义，同时又必须使用Unicode转义时启用，默认false",
		zh("write工具lenient_unescape参数的描述")
	)
}
