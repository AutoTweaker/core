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

package io.github.autotweaker.core.domain.agent.compact

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.BooleanSetting
import io.github.autotweaker.api.base.DoubleSetting
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.core.infrastructure.data.PromptSetting


object CompactSettings {
	@AutoService(SettingDef::class)
	class Prompt : PromptSetting(
		"compact", zh(
			"用于上下文压缩的提示词"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxCompactRetries : IntSetting(
		5, zh(
			"上下文压缩的最大重试次数，可能因为总结无效，可能因为LLM错误"
		)
	)
	
	@AutoService(SettingDef::class)
	class MinSummaryLength : IntSetting(
		50, zh(
			"上下文压缩输出的最小字符数，小于此值的总结会视为无效"
		)
	)
	
	@AutoService(SettingDef::class)
	class Thinking : BooleanSetting(
		false, zh(
			"上下文压缩时是否启用思考，不建议启用"
		)
	)
	
	@AutoService(SettingDef::class)
	class DefaultCompactContextUsage : DoubleSetting(
		0.85, zh(
			"自动上下文压缩的默认百分比阈值（根据上下文窗口）"
		)
	)
	
	@AutoService(SettingDef::class)
	class DefaultCompactTotalTokens : IntSetting(
		500_000, zh(
			"自动上下文压缩的默认 tokens 阈值"
		)
	)
}
