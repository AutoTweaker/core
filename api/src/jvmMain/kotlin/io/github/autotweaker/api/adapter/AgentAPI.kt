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
	val name: KebabId
	
	val model: ModelConfig
	val status: StateFlow<AgentStatus>
	val output: SharedFlow<SessionOutput>
	val context: StateFlow<SessionContext>
	val toolInfo: StateFlow<List<ToolInfo>>
	
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
