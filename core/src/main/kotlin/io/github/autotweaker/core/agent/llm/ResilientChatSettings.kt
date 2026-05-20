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

package io.github.autotweaker.core.agent.llm

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue

object ResilientChatSettings {
	@AutoService(SettingDef::class)
	object MaxRetries : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(3)
		override val description = "大模型请求的最大重试次数"
	}
	
	@AutoService(SettingDef::class)
	object RetryBaseDelaySeconds : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(1)
		override val description = "大模型请求重试前的基础等待时间（秒），多次重试会在此基础上累加（指数退避）"
	}
	
	@AutoService(SettingDef::class)
	object MaxRetryDelaySeconds : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(60)
		override val description = "大模型请求重试前的最大等待时间（秒），指数退避的上限"
	}
	
	@AutoService(SettingDef::class)
	object RetryJitterEnabled : SettingDef<SettingValue.ValBoolean> {
		override val default = SettingValue.ValBoolean(true)
		override val description = "大模型请求重试的等待时间是否加入随机抖动"
	}
}
