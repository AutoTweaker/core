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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.session.workspace.Workspace
import kotlinx.coroutines.sync.Mutex

internal interface AgentEnvironment {
	var context: AgentContext
	val contextMutex: Mutex
	suspend fun updateContext(transform: suspend (AgentContext) -> AgentContext)
	val agentState: MutableAgentState
	
	val tools: Tools
	val settings: List<SettingItem>
	val workspace: Workspace
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
	var processedTools: List<AgentContext.Message.Tool>? = null,
	val approvalReasons: MutableList<String> = mutableListOf(),
)
