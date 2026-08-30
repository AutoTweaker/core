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
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object WriteMessage {
	@AutoService(SettingDef::class)
	class InvalidHash : StringSetting(
		"无效的哈希：%s",
		zh("write工具sha256参数非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class FileExists : StringSetting(
		"文件 %s 已存在，如需覆写请使用read工具读取后提供sha256",
		zh("write工具目标文件已存在时的描述")
	)
	
	@AutoService(SettingDef::class)
	class HashMismatch : StringSetting(
		"覆盖文件 %s 失败，SHA256不匹配，文件已被外部更新，请重新读取文件",
		zh("write工具SHA256校验失败时的描述")
	)
	
	@AutoService(SettingDef::class)
	class InvalidEscape : StringSetting(
		"未知或不合法的转义：%s",
		zh("write工具转义非法时的描述")
	)
	
	@AutoService(SettingDef::class)
	class Created : StringSetting(
		"创建了文件 %s：\n%s",
		zh("write工具创建文件成功时的响应")
	)
	
	@AutoService(SettingDef::class)
	class Updated : StringSetting(
		"覆盖了文件 %s：\n%s",
		zh("write工具更新文件成功时的响应")
	)
	
	@AutoService(SettingDef::class)
	class Unchanged : StringSetting(
		"UNCHANGED",
		zh("write工具文件内容无变化时的diff占位文本")
	)
}
