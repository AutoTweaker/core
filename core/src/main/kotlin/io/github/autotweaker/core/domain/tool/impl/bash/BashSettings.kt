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
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

object BashSettings {
	@AutoService(SettingDef::class)
	class DefaultTimeoutSeconds : IntSetting(
		60,
		zh("bash-run工具默认超时时间（秒），可被LLM的参数覆盖")
	)
	
	@AutoService(SettingDef::class)
	class MaxOutput : IntSetting(
		20_000,
		zh("bash-run工具的最长输出长度（字符），超出将保留输出末尾阈值内部分，并将完整内容存入文件，此值分别对stdout和stderr应用，理论上进入上下文的完整输出最大达到此值的两倍")
	)
}
