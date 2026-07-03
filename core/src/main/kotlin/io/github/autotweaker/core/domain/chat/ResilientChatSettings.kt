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

package io.github.autotweaker.core.domain.chat

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.BooleanSetting
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object ResilientChatSettings {
	@AutoService(SettingDef::class)
	class MaxRetries : IntSetting(
		5, zh(
			"单轮大模型请求的最大重试次数"
		)
	)
	
	@AutoService(SettingDef::class)
	class LlmChatRetries : IntSetting(
		3, zh(
			"大模型请求的重试/回退策略耗尽后重头开始的最大次数"
		)
	)
	
	@AutoService(SettingDef::class)
	class RetryBaseDelaySeconds : IntSetting(
		1, zh(
			"大模型请求重试前的基础等待时间（秒），多次重试会在此基础上累加（指数退避）"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxRetryDelaySeconds : IntSetting(
		60, zh(
			"大模型请求重试前的最大等待时间（秒），指数退避的上限"
		)
	)
	
	@AutoService(SettingDef::class)
	class RetryJitterEnabled : BooleanSetting(
		true, zh(
			"大模型请求重试的等待时间是否加入随机抖动"
		)
	)
	
	@AutoService(SettingDef::class)
	class ChatRequestTimeout : IntSetting(
		300, zh(
			"大模型请求的默认总超时秒数"
		)
	)
	
	@AutoService(SettingDef::class)
	class ChatConnectTimeout : IntSetting(
		20, zh(
			"默认大模型请求建立连接的超时秒数"
		)
	)
	
	@AutoService(SettingDef::class)
	class ChatStreamChunkTimeout : IntSetting(
		30, zh(
			"大模型流式请求，两个数据块之间的默认最大等待秒数"
		)
	)
}
