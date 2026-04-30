package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.workspace.Workspace

internal interface AgentEnvironment {
	var context: AgentContext
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
)
