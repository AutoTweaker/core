package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.tool.ToolCallValidator

//实际调用工具
internal suspend fun executeApprovedToolPhase(
	env: AgentEnvironment,
	result: ToolCallValidator.ValidationResult.Success,
	call: AgentContext.CurrentRound.PendingToolCall,
): AgentContext.Message.Tool {
	env.updateStatus(AgentStatus.TOOL_CALLING)
	return try {
		env.tools.executeTool(result, call, env.provider, env.workspace)
	} catch (e: Exception) {
		buildErrorTool(call, e.message ?: "Tool execution failed")
	}
}
