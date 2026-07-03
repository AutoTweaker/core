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
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.core.infrastructure.data.PromptSetting


object BashSettings {
	@AutoService(SettingDef::class)
	class Description : StringSetting(
		"运行一条bash命令，可选设置超时时间并按id注入一次性环境变量", zh(
			"bash工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class RunFuncDescription : PromptSetting(
		"bash_run", zh(
			"bash_run工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class CommandPropDescription : StringSetting(
		"要执行的bash命令内容", zh(
			"bash_run工具command参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class TimeoutPropDescription : StringSetting(
		"命令超时时间（秒），必须大于0，默认%s秒", zh(
			"bash_run工具timeout_seconds参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class EnvIdsPropDescription : StringSetting(
		"要注入的环境变量列表，对于敏感信息，严禁令环境变量以任何形式打印到输出或写入文件中。可用列表：%s", zh(
			"bash_run工具env_ids参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class InvalidTimeoutMessage : StringSetting(
		"timeout_seconds必须大于0", zh(
			"bash_run工具timeout_seconds非法时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class InvalidCommandMessage : StringSetting(
		"command参数不能为空", zh(
			"bash_run工具command非法时的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class ResultTemplate : StringSetting(
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
			""".trimIndent(), zh(
			"bash_run工具执行结果模板，参数依次为退出码、执行时间（秒）、标准输出、标准错误"
		)
	)
	
	@AutoService(SettingDef::class)
	class DefaultTimeoutSeconds : IntSetting(
		120, zh(
			"bash_run工具默认超时时间（秒）"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxOutput : IntSetting(
		100_000, zh(
			"bash_run工具的最长输出长度，超出将保留输出末尾阈值内部分，并将完整内容存入文件，此值分别对stdout和stderr应用，理论上输出最大达到此值的两倍"
		)
	)
}
