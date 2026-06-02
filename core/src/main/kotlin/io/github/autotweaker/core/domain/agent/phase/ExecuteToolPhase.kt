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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

object ExecuteToolPhase {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun execute(
		env: AgentEnvironment,
		result: ToolCallValidator.ValidationResult.Success,
		call: AgentContext.CurrentRound.PendingToolCall,
	): AgentContext.Message.Tool {
		logger.debug(
			"Tool execution started  agentId={}  tool={}  timeout={}s",
			env.agentId,
			call.name,
			env.service.get(AgentToolSettings.TimeoutSeconds()).value
		)
		env.updateStatus(AgentStatus.TOOL_CALLING)
		val timeoutSeconds = env.service.get(AgentToolSettings.TimeoutSeconds()).value
		val timeoutMessage = env.service.get(AgentToolSettings.TimeoutMessage()).value
		return try {
			withTimeout((timeoutSeconds * 1000L).milliseconds) {
				env.tools.executeTool(
					result, call, ToolProvider.buildToolProvider(env), env.agentId,
					onToolDeactivated = { activeTools ->
						env.emitOutput(AgentOutput.ToolListUpdate(activeTools.map { it.meta.name }))
					},
					onToolOutput = { output ->
						env.emitOutput(output)
					},
				)
			}.also {
				logger.debug("Tool completed  agentId={}  tool={}  status={}", env.agentId, call.name, it.result.status)
			}
		} catch (_: TimeoutCancellationException) {
			logger.warn("Tool timed out  agentId={}  tool={}  timeout={}s", env.agentId, call.name, timeoutSeconds)
			ContextPhase.buildToolResult(
				call, timeoutMessage.format(timeoutSeconds), ToolResultStatus.TIMEOUT
			)
		} catch (_: CancellationException) {
			logger.debug("Tool cancelled  agentId={}  tool={}", env.agentId, call.name)
			ContextPhase.buildToolResult(
				call, env.toolCancelledMessage, ToolResultStatus.CANCELLED
			)
		} catch (e: Exception) {
			logger.error("Failed to execute tool  agentId={}  tool={}", env.agentId, call.name, e)
			ContextPhase.buildErrorTool(call, e.message ?: "Tool execution failed")
		}
	}
}
