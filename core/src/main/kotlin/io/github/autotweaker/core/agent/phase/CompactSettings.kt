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

package io.github.autotweaker.core.agent.phase

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.data.ResourcesLoader

object CompactSettings {
	@AutoService(SettingDef::class)
	class Prompt : SettingDef<SettingValue.ValString> {
		override val default by lazy { SettingValue.ValString(ResourcesLoader.loadPrompt("compact")) }
		override val description = "用于上下文压缩的提示词"
	}
	
	@AutoService(SettingDef::class)
	class MaxMessageChars : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(10000)
		override val description = "上下文压缩前对字符数大于此值的消息进行单独总结"
	}
	
	@AutoService(SettingDef::class)
	class MessageSummarizePrompt : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("请对以下消息内容进行概括，输出不要太长\n\n" + "<message>\n%s\n</message>")
		override val description = "上下文压缩前对字符数过多的消息进行单独总结时的提示词"
	}
	
	@AutoService(SettingDef::class)
	class MaxCompactRetries : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(5)
		override val description = "上下文压缩的最大重试次数"
	}
	
	@AutoService(SettingDef::class)
	class MinSummaryLength : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(50)
		override val description = "上下文压缩输出的最小字符数，小于此值的总结会视为无效"
	}
}
