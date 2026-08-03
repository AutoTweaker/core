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
import io.github.autotweaker.core.infrastructure.data.PromptSetting


object BashDesc {
	@AutoService(SettingDef::class)
	class Tool : StringSetting(
		"运行一条bash命令，可选设置超时时间并按id注入一次性环境变量", zh(
			"bash工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class Function : PromptSetting(
		"bash_run", zh(
			"bash-run工具的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class Command : StringSetting(
		"要执行的bash命令内容", zh(
			"bash-run工具command参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class Timeout : StringSetting(
		"命令超时时间（秒），必须大于0，默认%s秒", zh(
			"bash-run工具timeout_seconds参数的描述"
		)
	)
	
	@AutoService(SettingDef::class)
	class EnvIds : StringSetting(
		"要注入的环境变量列表，对于敏感信息，严禁令环境变量以任何形式打印到输出或写入文件中。可用列表：%s", zh(
			"bash-run工具env_ids参数的描述"
		)
	)
}
