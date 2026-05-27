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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.Collections

internal interface AgentEnvironment {
	val agentId: UUID
	val context: StateFlow<AgentContext>
	suspend fun updateContext(transform: suspend (AgentContext) -> AgentContext)
	val agentState: MutableAgentState
	
	val tools: Tools
	val service: SettingService
	val workspace: WorkspaceMeta
	val containerConfig: ContainerConfig
	
	val currentModel: Model
	val currentFallbackModels: List<Model>?
	val currentThinking: Boolean
	val summarizeModel: Model
	
	val toolCancelledMessage: String
	val toolRejectedMessage: String
	val toolRejectedWithFeedbackMessage: String
	
	val status: AgentStatus
	suspend fun emitOutput(output: AgentOutput)
	fun updateStatus(status: AgentStatus)
}

data class MutableAgentState(
	var pendingApproval: List<Tools.ToolCallResolveResult.NeedsApproval>? = null,
	@Volatile var processedTools: List<AgentContext.Message.Tool>? = null,
	val approvalReasons: MutableList<String> = Collections.synchronizedList(mutableListOf()),
)
