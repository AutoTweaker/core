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

package io.github.autotweaker.core.domain.tool.impl.bash

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object BashMessage {
	@AutoService(SettingDef::class)
	class InvalidTimeout : StringSetting(
		"timeout_seconds必须大于0", zh(
			"bash-run工具timeout_seconds非法时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class InvalidCommand : StringSetting(
		"command参数不能为空", zh(
			"bash-run工具command非法时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class EnvNotFound : StringSetting(
		"不存在名为%s的环境变量", zh(
			"bash-run工具请求注入的环境变量不存在时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class ToolResult : StringSetting(
		"""
			命令已执行，退出码：%s，执行时间：%s
			
			标准输出：
			<stdout>
			%s
			</stdout>
			
			标准错误：
			<stderr>
			%s
			</stderr>
			""".trimIndent(), zh(
			"bash-run工具执行结果模板，参数依次为退出码、执行时间（秒）、标准输出、标准错误"
		)
	)
}
