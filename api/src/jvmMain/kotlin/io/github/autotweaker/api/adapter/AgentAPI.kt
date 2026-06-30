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

import io.github.autotweaker.api.types.KebabCase
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
 *
 * 关于 agent 上下文的基本概念（如“轮次”）请参阅 [SessionContextIndex]
 */
interface AgentAPI {
	/**
	 * agent 的 id，永远由 [UUID.randomUUID] 生成。
	 */
	val id: UUID
	
	/**
	 * agent 的名称，用于多 agent 场景下的显示区分。
	 *
	 * @see KebabCase
	 */
	val name: KebabCase
	
	/**
	 * agent 的模型配置，每个 agent 可以拥有独立的模型配置。
	 *
	 * 可通过 [setModel] 修改模型配置。此属性永远反映获取时的最新状态。
	 */
	val model: ModelConfig
	
	/**
	 * agent 的实时状态，参见 [AgentStatus]。
	 *
	 * 关于实时输出请见 [output]。
	 *
	 * @see AgentStatus
	 */
	val status: StateFlow<AgentStatus>
	
	/**
	 * agent 的实时输出流，通常为无需持久化的流式数据块或错误信息。
	 *
	 * 关于状态信息请见 [status]，关于上下文信息请见 [context]。
	 *
	 * @see SessionOutput
	 */
	val output: SharedFlow<SessionOutput>
	
	/**
	 * agent 的实时上下文，完整的消息、工具调用请求均在此索引。
	 *
	 * 关于流式数据请见 [output]。
	 *
	 * @see SessionContext
	 * @see SessionContextIndex
	 */
	val context: StateFlow<SessionContext>
	
	/**
	 * 实时的工具列表，列表中只有各值的 `active` 属性会变化，列表本身不会变化。
	 *
	 * 关于工具相关的机制，请见 [io.github.autotweaker.api.tool.Tool]。
	 *
	 * @see ToolInfo
	 */
	val toolInfo: StateFlow<List<ToolInfo>>
	
	/**
	 * 向 agent 发送消息，无论 agent 状态如何，消息将始终进入队列。
	 * 若 agent 空闲（[AgentStatus.FREE]），消息将被立即消费，触发 agent 的事件循环。
	 *
	 * 否则，队列中的消息将在下一次 LLM 请求，也就是 [AgentStatus.THINKING] 阶段前被消费。
	 *
	 * 无论队列中有多少条消息，消费时都会将它们合并，这之中也可能包含由 AutoTweaker 自动发送的系统消息。
	 *
	 * 使用 `send(content).await()` 来挂起等待 agent 消费这条消息，并获取合并后的那条 [io.github.autotweaker.api.types.session.SessionMessage.User] 的 id 用于前端展示。
	 *
	 * 发送一条只包含 [MessageContent.injections] 的消息可以注入一些即时信息。
	 *
	 * @see MessageContent
	 */
	fun send(content: MessageContent): Delivery
	
	/**
	 * 令 agent 在当前任务完成之后停下，并归档当前上下文。状态将变为 [AgentStatus.FREE]。
	 *
	 * “当前任务”包括 LLM 请求和工具调用（单个工具），未执行的工具调用将被取消。
	 *
	 * 后台的上下文压缩任务不受此影响，要终止正在进行的上下文压缩任务，请使用 [cancelCompact] api。
	 */
	suspend fun pause(): AgentAPI
	
	/**
	 * 立即停止 agent 的工具调用或 LLM 请求，并归档当前上下文，等待状态变为 [AgentStatus.FREE]（Stop 完成）。
	 *
	 * 未批准的工具调用将被取消，正在执行的 LLM 请求将被中断，响应将被丢弃，当前轮次若只有用户消息，当前轮次将被丢弃。
	 *
	 * 请注意，终止正在进行的 LLM 请求将导致 Usage 无法被记录。
	 *
	 * 后台的上下文压缩任务不受此影响，要终止正在进行的上下文压缩任务，请使用 [cancelCompact] api。
	 */
	suspend fun stop(): AgentAPI
	
	/**
	 * 触发 agent 的上下文压缩，如果上下文压缩正在进行，不会重复触发，上下文压缩在后台进行，此方法不会挂起等待上下文压缩完毕。
	 *
	 * 除此之外，agent 会在每次 LLM 请求、工具调用结束后自动根据配置的阈值检查当前 Usage 并自动触发上下文压缩。
	 *
	 * agent 的上下文压缩完全在后台进行，不会阻塞 agent 的主事件循环，上下文压缩完毕后将在下一次 LLM 请求立即生效。
	 */
	suspend fun compact(): AgentAPI
	
	/**
	 * 取消正在进行的上下文压缩进程。
	 *
	 * 请注意，只要达到阈值，上下文压缩仍然会继续自动触发。
	 */
	suspend fun cancelCompact(): AgentAPI
	
	/**
	 * 取消正在进行的工具调用。
	 *
	 * 请注意，只要被批准，剩余工具调用仍然会继续执行。
	 */
	suspend fun cancelTool(): AgentAPI
	
	/**
	 * 更新 agent 使用的大模型，下次请求立即生效。
	 *
	 * 要获取当前的大模型配置，请访问 [model]。
	 */
	suspend fun setModel(config: ModelConfig): AgentAPI
	
	/**
	 * 批准一个工具调用请求。
	 *
	 * AutoTweaker 会根据 LLM 请求的顺序，逐一等待批准，并逐一执行，乱序的批准将被缓存。
	 *
	 * 工具调用会始终根据 LLM 请求的顺序执行。
	 */
	suspend fun approve(approval: ToolApprove): AgentAPI
	
	/**
	 * 在 agent 的上下文中注入 XML 标签，相同 id 的标签会去重。
	 *
	 * XML 标签会被注入到当前上下文中的第一条用户消息中，此 api 用于注入不变的系统提示，如果要注入动态的实时信息，请使用 [send]。
	 */
	suspend fun inject(injection: ContextInjection): AgentAPI
	
	/**
	 * 从 agent 的上下文中移除一条标签注入。
	 * 
	 * 如果请求移除一条不存在的标签，什么也不会发生。
	 */
	suspend fun removeInjection(id: UUID): AgentAPI
}
