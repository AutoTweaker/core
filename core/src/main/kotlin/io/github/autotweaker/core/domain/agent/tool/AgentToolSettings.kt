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

package io.github.autotweaker.core.domain.agent.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue

object AgentToolSettings {
	@AutoService(SettingDef::class)
	class Cancelled : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("工具调用已取消")
		override val description = "工具调用被取消时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class Rejected : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString(
			"工具调用已被用户拒绝，工具未被执行。请停止当前操作；向用户解释为什么要执行这个操作；询问用户意见；等待用户告知如何继续"
		)
		override val description = "工具调用被拒绝时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class RejectedWithFeedback : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("工具未被执行，用户拒绝了工具调用，并留言：%s")
		override val description = "工具调用被拒绝时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class PropertyMissing : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("%s工具需要属性：%s")
		override val description = "工具调用缺少属性时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class PropertyError : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("%s工具的属性%s必须为%s类型")
		override val description = "工具调用属性格式错误时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class FunctionNameError : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("%s工具不存在，请检查工具是否已激活")
		override val description = "调用工具不存在时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class JsonError : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("调用参数不是一个有效的JSON对象：%s")
		override val description = "工具调用参数无法解析时的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class ReasonDescription : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("简要描述调用此工具的目的")
		override val description = "工具调用的reason属性描述"
	}
	
	@AutoService(SettingDef::class)
	class TimeoutSeconds : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(600)
		override val description = "工具调用超时时间，单位秒"
	}
	
	@AutoService(SettingDef::class)
	class TimeoutMessage : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("工具调用超时（%s秒）")
		override val description = "工具调用超时后的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class EnableDescription : SettingDef<SettingValue.ValString> {
		override val default = SettingValue.ValString("激活此工具以开始使用，无论将此值设为true或false都将启用工具")
		override val description = "未激活工具的enable属性描述"
	}
	
	@AutoService(SettingDef::class)
	class ActiveMessage : SettingDef<SettingValue.ValString> {
		override val default =
			SettingValue.ValString("工具已激活，包含这些function：[%s]\n注意：名为[%s]的function已不再可用，检查你的工具列表来了解新的function和使用方法")
		override val description = "激活工具后的ToolResult"
	}
	
	@AutoService(SettingDef::class)
	class DeactivationThreshold : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(50)
		override val description = "工具将在连续指定次数未使用后被自动禁用，设为0以禁用此特性"
	}
}
