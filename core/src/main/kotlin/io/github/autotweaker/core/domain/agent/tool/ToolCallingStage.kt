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

package io.github.autotweaker.core.domain.agent.tool

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import kotlinx.coroutines.*
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ToolCallingStage(
	private val agentId: UUID,
	private val tools: Tools,
	private val workspace: WorkspaceMeta,
	private val onOutput: (AgentOutput) -> Unit,
	private val onToolCall: (String?) -> Unit
) : Loggable, Traceable, Settable {
	@Volatile
	private var toolJob: Job? = null
	
	fun cancelToolJob() {
		toolJob?.cancel()
		toolJob = null
	}
	
	suspend fun execute(
		call: ThinkingStage.ResolvedToolCall,
		model: AgentModel,
		context: AgentContext,
	): AgentContext.Message.Tool.Result {
		val timeoutSeconds = setting(AgentToolSettings.TimeoutSeconds())
		val timeoutMessage = setting(AgentToolSettings.TimeoutMessage())
		val cancelledMessage = setting(AgentToolSettings.Cancelled())
		
		val startTime = TimeSource.Monotonic.markNow()
		return trace.catching {
			coroutineScope {
				toolJob = coroutineContext[Job]
				onToolCall(call.pendingCall.callId)
				withTimeout(timeoutSeconds.seconds) {
					val provider = ToolProvider.buildToolProvider(
						workspace = workspace,
						onOutput = onOutput,
						model = model,
						context = context
					)
					
					tools.executeTool(
						toolName = call.validated.toolName,
						callId = call.pendingCall.callId,
						arguments = call.validated.args,
						provider = provider,
						onToolOutput = onOutput
					).andLog(log) {
						info(
							"Called tool  agentId={}  tool={}  status={}",
							agentId, call.validated.toolName, it.status
						)
					}
				}
			}
		}.also {
			toolJob = null
			onToolCall(null)
		}.rethrow<SecretStoreLockedException>()
			.getOrElse { e ->
				when (e) {
					is TimeoutCancellationException -> {
						val elapsed = startTime.elapsedNow().inWholeSeconds
						log.warn(
							"Failed tool execution  agentId={}  tool={}  reason=TIMEOUT  elapsed={}s",
							agentId, call.pendingCall.name, elapsed
						)
						buildToolResult(timeoutMessage.format(elapsed), ToolResultStatus.TIMEOUT)
					}
					
					is CancellationException -> {
						log.debug(
							"Failed tool execution  agentId={}  tool={}  reason=CANCELLED",
							agentId,
							call.pendingCall.name
						)
						buildToolResult(cancelledMessage, ToolResultStatus.CANCELLED)
					}
					
					else -> {
						log.error("Failed tool execution  agentId={}  tool={}", agentId, call.pendingCall.name, e)
						buildToolResult(e.message ?: "Tool execution failed", ToolResultStatus.FAILURE)
					}
				}
			}
	}
	
	private fun buildToolResult(
		content: String,
		status: ToolResultStatus,
	): AgentContext.Message.Tool.Result = AgentContext.Message.Tool.Result(
		content = content,
		timestamp = Clock.System.now(),
		status = status
	)
}
