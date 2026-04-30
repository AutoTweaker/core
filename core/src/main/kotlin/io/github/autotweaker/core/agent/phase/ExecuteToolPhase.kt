package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.data.settings.find
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

//实际调用工具
internal suspend fun executeApprovedToolPhase(
	env: AgentEnvironment,
	result: ToolCallValidator.ValidationResult.Success,
	call: AgentContext.CurrentRound.PendingToolCall,
): AgentContext.Message.Tool {
	env.updateStatus(AgentStatus.TOOL_CALLING)
	val timeoutSeconds: Int = env.settings.find("core.agent.tool.timeout.seconds")
	val timeoutMessage: String = env.settings.find("core.agent.tool.response.timeout")
	return try {
		withTimeout((timeoutSeconds * 1000L).milliseconds) {
			env.tools.executeTool(result, call, buildToolProvider(env), env.workspace)
		}
	} catch (_: kotlinx.coroutines.TimeoutCancellationException) {
		buildToolResult(call, timeoutMessage.format(timeoutSeconds), AgentContext.Message.Tool.Result.Status.TIMEOUT)
	} catch (e: Exception) {
		buildErrorTool(call, e.message ?: "Tool execution failed")
	}
}
