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
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader

object BashSettings {
	@AutoService(SettingDef::class)
	class Description : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("运行一条bash命令，可选设置超时时间并按id注入一次性环境变量")
		override val description = "bash工具的描述"
	}
	
	@AutoService(SettingDef::class)
	class RunFuncDescription : SettingDef<SettingValue.ValString> {
		override val default by lazy { SettingValue.ValString(ResourcesLoader.loadPrompt("bash_run")) }
		override val description = "bash_run工具的描述"
	}
	
	@AutoService(SettingDef::class)
	class CommandPropDescription : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("要执行的bash命令内容")
		override val description = "bash_run工具command参数的描述"
	}
	
	@AutoService(SettingDef::class)
	class TimeoutPropDescription : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("命令超时时间（秒），必须大于0，默认%s秒")
		override val description = "bash_run工具timeout_seconds参数的描述"
	}
	
	@AutoService(SettingDef::class)
	class EnvIdsPropDescription : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString(
			"要注入的环境变量列表，对于敏感信息，严禁令环境变量以任何形式打印到输出或写入文件中。可用列表：%s"
		)
		override val description = "bash_run工具env_ids参数的描述"
	}
	
	@AutoService(SettingDef::class)
	class InvalidTimeoutMessage : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("timeout_seconds必须大于0")
		override val description = "bash_run工具timeout_seconds非法时的描述"
	}
	
	@AutoService(SettingDef::class)
	class InvalidCommandMessage : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("command参数不能为空")
		override val description = "bash_run工具command非法时的描述"
	}
	
	@AutoService(SettingDef::class)
	class ResultTemplate : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString(
			"""
			命令已执行，退出码：%s，执行时间：%s秒
			
			标准输出：
			<stdout>
			%s
			</stdout>
			
			标准错误：
			<stderr>
			%s
			</stderr>
			""".trimIndent()
		)
		override val description = "bash_run工具执行结果模板，参数依次为退出码、执行时间（秒）、标准输出、标准错误"
	}
	
	@AutoService(SettingDef::class)
	class DefaultTimeoutSeconds : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(60)
		override val description = "bash_run工具默认超时时间（秒）"
	}
}
