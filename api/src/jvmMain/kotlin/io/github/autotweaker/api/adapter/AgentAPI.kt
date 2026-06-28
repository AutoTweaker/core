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

package io.github.autotweaker.api.adapter

import io.github.autotweaker.api.types.KebabId
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionOutput
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * 用于管理单个 agent 实例的 api。
 *
 * 大部分方法都重新返回自身（[AgentAPI]）以支持链式调用。
 */
interface AgentAPI {
	val id: UUID
	
	/**
	 * agent 的名称，用于多 agent 场景下的显示区分。
	 *
	 * @see KebabId
	 */
	val name: KebabId
	
	/**
	 * agent 的模型配置，每个 agent 可以拥有独立的模型配置。
	 */
	val model: ModelConfig
	
	/**
	 * agent 的实时状态。
	 *
	 * @see AgentStatus
	 */
	val status: StateFlow<AgentStatus>
	
	/**
	 * agent 的实时输出流，通常为无需持久化的流式数据块或错误信息。
	 *
	 * @see SessionOutput
	 */
	val output: SharedFlow<SessionOutput>
	
	/**
	 * agent 的实时上下文，完整的消息、工具调用请求均在此索引。
	 *
	 * @see SessionContext
	 * @see SessionContextIndex
	 */
	val context: StateFlow<SessionContext>
	
	/**
	 * 实时的工具列表，会变化的主要是 `active` 属性，其余属性以及列表长度通常不会变化
	 *
	 * @see ToolInfo
	 */
	val toolInfo: StateFlow<List<ToolInfo>>
	
	/**
	 * 向 agent 发送消息，无论 agent 状态如何，消息将始终进入队列。
	 * 若 agent 空闲（[AgentStatus.FREE]），消息将被立即消费，触发 agent 的事件循环。
	 * 否则，队列中的消息将在下一次 LLM 请求，也就是 [AgentStatus.THINKING] 阶段前被消费。
	 *
	 * 无论队列中有多少条消息，消费时都会将它们合并，这之中也可能包含由 AutoTweaker 自动发送的系统消息。
	 *
	 * 使用 `send(content).await()` 来挂起等待 agent 消费这条消息，并获取合并后的那条 [io.github.autotweaker.api.types.session.SessionMessage.User] 的 id 用于前端展示。
	 */
	fun send(content: MessageContent): Delivery
	
	suspend fun pause(): AgentAPI
	suspend fun stop(): AgentAPI
	suspend fun compact(): AgentAPI
	suspend fun cancelCompact(): AgentAPI
	suspend fun cancelTool(): AgentAPI
	
	suspend fun setModel(config: ModelConfig): AgentAPI
	suspend fun approve(approval: ToolApprove): AgentAPI
	
	suspend fun inject(injection: ContextInjection): AgentAPI
	suspend fun removeInjection(id: UUID): AgentAPI
}
