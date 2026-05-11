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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.data.settings.find
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

internal object ExecuteToolPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	internal suspend fun execute(
		env: AgentEnvironment,
		result: ToolCallValidator.ValidationResult.Success,
		call: AgentContext.CurrentRound.PendingToolCall,
	): AgentContext.Message.Tool {
		logger.debug(
			"Tool execution started  agentId={}  tool={}  timeout={}s",
			env.agentId, call.name, env.settings.find<Int>("core.agent.tool.timeout.seconds")
		)
		env.updateStatus(AgentStatus.TOOL_CALLING)
		val timeoutSeconds: Int = env.settings.find("core.agent.tool.timeout.seconds")
		val timeoutMessage: String = env.settings.find("core.agent.tool.response.timeout")
		return try {
			withTimeout((timeoutSeconds * 1000L).milliseconds) {
				env.tools.executeTool(
					result, call, ToolProvider.buildToolProvider(env), env.workspace, env.agentId,
					onToolActivated = { activeTools ->
						env.emitOutput(AgentOutput.ToolListUpdate(activeTools))
					},
					onToolOutput = { output ->
						env.emitOutput(output)
					},
				)
			}.also {
				logger.debug("Tool completed  agentId={}  tool={}  status={}", env.agentId, call.name, it.result.status)
			}
		} catch (_: kotlinx.coroutines.TimeoutCancellationException) {
			logger.warn("Tool timed out  agentId={}  tool={}  timeout={}s", env.agentId, call.name, timeoutSeconds)
			ContextPhase.buildToolResult(
				call,
				timeoutMessage.format(timeoutSeconds),
				AgentContext.Message.Tool.Result.Status.TIMEOUT
			)
		} catch (_: kotlinx.coroutines.CancellationException) {
			logger.debug("Tool cancelled  agentId={}  tool={}", env.agentId, call.name)
			ContextPhase.buildToolResult(
				call,
				env.toolCancelledMessage,
				AgentContext.Message.Tool.Result.Status.CANCELLED
			)
		} catch (e: Exception) {
			logger.error("Failed to execute tool  agentId={}  tool={}", env.agentId, call.name, e)
			ContextPhase.buildErrorTool(call, e.message ?: "Tool execution failed")
		}
	}
}
