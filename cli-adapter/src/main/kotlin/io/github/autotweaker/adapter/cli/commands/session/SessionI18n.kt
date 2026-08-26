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

package io.github.autotweaker.adapter.cli.commands.session

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.en
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n.I18nDef

object SessionI18n {
	@AutoService(I18nDef::class)
	class Desc : I18nBase(
		zh("管理和进入会话"),
	)
	
	@AutoService(I18nDef::class)
	class WorkspaceParam : I18nBase(
		zh("工作区的名称，默认当前目录下的工作区，无可用则默认工作区"),
	)
	
	@AutoService(I18nDef::class)
	class ListFlag : I18nBase(
		zh("列出工作区下的所有会话"),
	)
	
	@AutoService(I18nDef::class)
	class NewFlag : I18nBase(
		zh("创建新会话"),
	)
	
	@AutoService(I18nDef::class)
	class SendFlag : I18nBase(
		zh("通过stdin向指定的会话发送消息"),
	)
	
	@AutoService(I18nDef::class)
	class ApproveFlag : I18nBase(
		zh("批准指定会话的工具调用"),
	)
	
	@AutoService(I18nDef::class)
	class RejectFlag : I18nBase(
		zh("拒绝指定会话的工具调用"),
	)
	
	@AutoService(I18nDef::class)
	class ReasonParam : I18nBase(
		zh("附言"),
	)
	
	@AutoService(I18nDef::class)
	class YoloFlag : I18nBase(
		zh("自动批准指定会话的任何工具调用"),
	)
	
	@AutoService(I18nDef::class)
	class ViewFlag : I18nBase(
		zh("查看指定会话"),
	)
	
	@AutoService(I18nDef::class)
	class DeleteFlag : I18nBase(
		zh("通过id删除一个会话"),
	)
	
	@AutoService(I18nDef::class)
	class ContainerWorkspaceFormat : I18nBase(
		zh("容器内工作区: %s ('%s')"),
	)
	
	@AutoService(I18nDef::class)
	class WorkspaceFormat : I18nBase(
		zh("工作区: %s ('%s')"),
	)
	
	@AutoService(I18nDef::class)
	class NoSessions : I18nBase(
		zh("当前工作区没有会话"),
	)
	
	@AutoService(I18nDef::class)
	class SessionId : I18nBase(
		zh("会话 id: %s"),
	)
	
	@AutoService(I18nDef::class)
	class SessionTitle : I18nBase(
		zh("会话标题: %s"),
	)
	
	@AutoService(I18nDef::class)
	class MessageCount : I18nBase(
		zh("消息数量: %s"),
	)
	
	@AutoService(I18nDef::class)
	class IdRestartWarning : I18nBase(
		zh("会话与 id 的对应关系将会在程序重启后失效"),
	)
	
	@AutoService(I18nDef::class)
	class SessionCreated : I18nBase(
		zh("创建了会话 %s"),
	)
	
	@AutoService(I18nDef::class)
	class SessionNotFound : I18nBase(
		zh("找不到会话 %s"),
	)
	
	@AutoService(I18nDef::class)
	class SessionDeleted : I18nBase(
		zh("删除了会话 %s"),
	)
	
	@AutoService(I18nDef::class)
	class UserEnvironment : I18nBase(
		zh("用户交互环境：Command-Line Interface\n应当减少或避免markdown输出，使用纯文本进行交流"),
	)
	
	@AutoService(I18nDef::class)
	class MessageSent : I18nBase(
		zh("发送了消息，等待接收..."),
	)
	
	@AutoService(I18nDef::class)
	class MessageDropped : I18nBase(
		zh("消息被丢弃"),
	)
	
	@AutoService(I18nDef::class)
	class MessageReceived : I18nBase(
		zh("消息已接收:"),
	)
	
	@AutoService(I18nDef::class)
	class YoloContainerOnly : I18nBase(
		zh("仅支持对容器内工作区进行自动批准"),
	)
	
	@AutoService(I18nDef::class)
	class YoloStart : I18nBase(
		zh("开始自动批准会话 %s 中的工具调用请求"),
	)
	
	@AutoService(I18nDef::class)
	class ToolApproved : I18nBase(
		zh("批准了工具 %s 的调用请求"),
	)
	
	@AutoService(I18nDef::class)
	class NoPendingCalls : I18nBase(
		zh("没有待批准的工具调用"),
	)
	
	@AutoService(I18nDef::class)
	class CallApproved : I18nBase(
		zh("批准了 %s 调用"),
	)
	
	@AutoService(I18nDef::class)
	class CallRejected : I18nBase(
		zh("拒绝了 %s 调用"),
	)
	
	@AutoService(I18nDef::class)
	class SessionIdInvalid : I18nBase(
		zh("找不到会话 %s, 程序重启后需要重新运行 list 才能生成 id"),
	)
	
	@AutoService(I18nDef::class)
	class SessionEntered : I18nBase(
		zh("已进入会话 %s"),
	)
	
	@AutoService(I18nDef::class)
	class Thinking : I18nBase(
		zh("Thinking..."),
		en("Thinking..."),
	)
	
	@AutoService(I18nDef::class)
	class AgentError : I18nBase(
		zh("Agent 错误: %s"),
	)
	
	@AutoService(I18nDef::class)
	class LlmError : I18nBase(
		zh("LLM 错误: %s"),
	)
	
	@AutoService(I18nDef::class)
	class UnknownException : I18nBase(
		zh("未知异常"),
	)
	
	@AutoService(I18nDef::class)
	class CorruptMessage : I18nBase(
		zh("[损坏的消息: %s]"),
	)
	
	@AutoService(I18nDef::class)
	class Usage : I18nBase(
		zh("输入 %s tokens | 输出 %s tokens | 缓存命中率 %s"),
	)
}
