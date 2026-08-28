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
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object ToolSettings {
	const val TOOL_NOT_EXECUTED =
		"工具未被执行，文件系统与外部环境处于工具调用之前的状态，工具没有对文件系统或外部环境产生任何影响"
	
	@AutoService(SettingDef::class)
	class CancelledPending : StringSetting(
		"工具调用已被取消，$TOOL_NOT_EXECUTED",
		zh("工具调用被取消时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class CancelledExecuting : StringSetting(
		"工具调用在执行时被取消，工具可能已经对文件系统或外部环境产生影响，在继续之前请完成确认",
		zh("工具调用被取消时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class Rejected : StringSetting(
		"工具调用已被用户拒绝，$TOOL_NOT_EXECUTED。" +
				"用户不希望执行当前操作，请确保执行的操作没有超出你的权限范围，没有违背用户意图，并使用其他方式继续。",
		zh("工具调用被拒绝时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class RejectedWithFeedback : StringSetting(
		"$TOOL_NOT_EXECUTED。用户拒绝了工具调用，并留言：%s",
		zh("工具调用被拒绝，有原因时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class PropertyMissing : StringSetting(
		"%s工具需要属性：%s\n$TOOL_NOT_EXECUTED",
		zh("工具调用缺少属性时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class DeserializationError : StringSetting(
		"%s工具的参数无效：%s\n\n$TOOL_NOT_EXECUTED",
		zh("工具调用参数反序列化失败时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class FunctionNameError : StringSetting(
		"%s工具不存在，请检查工具是否已激活\n$TOOL_NOT_EXECUTED",
		zh("调用工具不存在时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class ToolAlreadyActiveError : StringSetting(
		"%s工具已经激活，请不要重复激活，已展开的可用子函数：[%s]\n$TOOL_NOT_EXECUTED",
		zh("激活已激活工具时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class JsonError : StringSetting(
		"调用参数不是一个有效的JSON对象：%s\n\n$TOOL_NOT_EXECUTED",
		zh("工具调用参数无法解析时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class ReasonDescription : StringSetting(
		"简要描述调用此工具的目的",
		zh("工具调用的reason属性描述")
	)
	
	@AutoService(SettingDef::class)
	class ReasonEmptyError : StringSetting(
		"reason为空或过短，请提供有效的reason\n$TOOL_NOT_EXECUTED",
		zh("工具调用的reason属性为空时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class ToolResolveError : StringSetting(
		"调用参数在解析时出错：%s\n\n$TOOL_NOT_EXECUTED",
		zh("工具调用解析抛出异常时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class ToolExecutionError : StringSetting(
		"工具执行时出错：%s",
		zh("工具调用抛出异常时的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class ReasonLength : IntSetting(
		5,
		zh("工具调用的reason属性的最少字符数")
	)
	
	@AutoService(SettingDef::class)
	class TimeoutSeconds : IntSetting(
		1800,
		zh("工具调用超时时间，单位秒，超时后工具将中止并丢弃响应，谨慎设置")
	)
	
	@AutoService(SettingDef::class)
	class TimeoutMessage : StringSetting(
		"工具调用超时（%s），工具可能已经对文件系统或外部环境产生影响，在继续之前请完成确认",
		zh("工具调用超时后的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class EnableDescription : StringSetting(
		"请传递此参数来激活这个工具\n无论将此值设为true或false都将激活工具\n激活后工具将可用，工具列表将会更新",
		zh("未激活工具的enable属性描述")
	)
	
	@AutoService(SettingDef::class)
	class ActiveMessage : StringSetting(
		"工具已激活，包含这些子函数：[%s]\n" +
				"注意：名为[%s]的函数已不再可用，检查你的工具列表来了解新的函数和使用方法",
		zh("激活工具后的ToolResult")
	)
	
	@AutoService(SettingDef::class)
	class AutoDeactivateMessage : StringSetting(
		"这些工具一段时间未被调用，被取消激活，请在需要时重新激活：%s",
		zh("自动取消激活工具时发送的消息")
	)
	
	@AutoService(SettingDef::class)
	class DeactivationThreshold : IntSetting(
		50,
		zh("工具将在连续指定次数未使用后被自动禁用，设为0以禁用此特性")
	)
	
	@AutoService(SettingDef::class)
	class MaxOutput : IntSetting(
		100_000, zh(
			"工具输出的极限长度，单位字符数而非token，任何工具在输出超过此长度时都将被截断，完整输出会被存入临时文件。" +
					"请不要设置较小的值，也不要比read等工具的上限更小，如果此值比read工具的字符数上限小，" +
					"可能导致模型在读取完整输出时再次触发截断和保存"
		)
	)
}
