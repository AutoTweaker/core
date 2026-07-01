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
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.config.SettingDef

object AgentToolSettings {
	const val TOOL_NOT_EXECUTED =
		"工具未被执行，文件系统与外部环境处于工具调用之前的状态，工具没有对文件系统或外部环境产生任何影响"
	
	@AutoService(SettingDef::class)
	class Cancelled : StringSetting(
		"工具调用已取消，$TOOL_NOT_EXECUTED", "工具调用被取消时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class Rejected : StringSetting(
		"工具调用已被用户拒绝，$TOOL_NOT_EXECUTED。请停止当前操作；向用户解释为什么要执行这个操作；询问用户意见；等待用户告知如何继续",
		"工具调用被拒绝时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class RejectedWithFeedback : StringSetting(
		"$TOOL_NOT_EXECUTED，用户拒绝了工具调用，并留言：%s", "工具调用被拒绝，有原因时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class PropertyMissing : StringSetting(
		"%s工具需要属性：%s\n$TOOL_NOT_EXECUTED", "工具调用缺少属性时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class DeserializationError : StringSetting(
		"%s工具的参数无效：%s\n$TOOL_NOT_EXECUTED", "工具调用参数反序列化失败时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class FunctionNameError : StringSetting(
		"%s工具不存在，请检查工具是否已激活\n$TOOL_NOT_EXECUTED", "调用工具不存在时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class JsonError : StringSetting(
		"调用参数不是一个有效的JSON对象：%s\n$TOOL_NOT_EXECUTED", "工具调用参数无法解析时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class ReasonDescription : StringSetting(
		"简要描述调用此工具的目的", "工具调用的reason属性描述"
	)
	
	@AutoService(SettingDef::class)
	class ReasonEmptyError : StringSetting(
		"reason不能为空，请提供reason\n$TOOL_NOT_EXECUTED", "工具调用的reason属性为空时的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class TimeoutSeconds : IntSetting(
		1800, "工具调用超时时间，单位秒，超时后工具将中止并丢弃响应，谨慎设置"
	)
	
	@AutoService(SettingDef::class)
	class TimeoutMessage : StringSetting(
		"工具调用超时（%s秒），工具可能已经对文件系统或外部环境产生影响，在继续之前请完成确认", "工具调用超时后的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class EnableDescription : StringSetting(
		"激活此工具以开始使用，无论将此值设为true或false都将启用工具", "未激活工具的enable属性描述"
	)
	
	@AutoService(SettingDef::class)
	class ActiveMessage : StringSetting(
		"工具已激活，包含这些function：[%s]\n注意：名为[%s]的function已不再可用，检查你的工具列表来了解新的function和使用方法",
		"激活工具后的ToolResult"
	)
	
	@AutoService(SettingDef::class)
	class DeactivationThreshold : IntSetting(
		50, "工具将在连续指定次数未使用后被自动禁用，设为0以禁用此特性"
	)
}
