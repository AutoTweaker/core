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
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Result
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ToolCallingStage(
	private val agentId: UUID,
	private val tools: Tools,
	private val workspace: () -> Path,
	private val truncation: TruncationService,
	private val onOutput: (RuntimeOutput) -> Unit,
	private val onToolCall: (Pair<String, List<UiBlock>>?) -> Unit
) : Loggable, Traceable {
	@Volatile
	private var toolJob: Job? = null
	
	fun cancelToolJob() {
		toolJob?.cancel()
		toolJob = null
	}
	
	suspend fun execute(
		call: RuntimeContext.CurrentRound.PendingToolCall,
		resolved: Tool.ResolveResult.Ready,
		model: AgentModel,
		context: RuntimeContext,
	): Result {
		val timeoutSeconds = ToolSettings.TimeoutSeconds().get()
		
		val startTime = TimeSource.Monotonic.markNow()
		return trace.catching {
			coroutineScope {
				toolJob = coroutineContext[Job]
				onToolCall(call.callId to resolved.executing())
				withTimeout(timeoutSeconds.seconds) {
					val provider = ToolProvider.buildToolProvider(
						workspace = workspace,
						onOutput = onOutput,
						model = model,
						context = context,
						truncation = truncation,
					)
					
					tools.executeTool(
						toolName = call.validatedToolName,
						callId = call.callId,
						request = resolved.result,
						provider = provider,
						onToolOutput = onOutput,
						truncation = truncation,
					).andLog(log) {
						info(
							"Called tool  agentId={}  tool={}  status={}",
							agentId, call.validatedToolName, it.status
						)
					}
				}
			}
		}.also {
			toolJob = null
			onToolCall(null)
		}.recoverException { _: TimeoutCancellationException ->
			val elapsed = startTime.elapsedNow()
			log.warn(
				"Failed tool execution  agentId={}  tool={}  reason=TIMEOUT  elapsed={}",
				agentId, call.validatedToolName, elapsed
			)
			buildToolResult(
				ToolSettings.TimeoutMessage().format(elapsed),
				resolved.timeout(elapsed),
				ToolResultStatus.TIMEOUT
			)
		}.recoverException { _: CancellationException ->
			log.debug(
				"Failed tool execution  agentId={}  tool={}  reason=CANCELLED",
				agentId,
				call.validatedToolName
			)
			buildToolResult(
				ToolSettings.CancelledExecuting().get(),
				resolved.cancelled(),
				ToolResultStatus.CANCELLED
			)
		}.getOrElse { e ->
			log.error(
				"Failed tool execution  agentId={}  tool={}",
				agentId,
				call.validatedToolName,
				e
			)
			buildToolResult(
				ToolSettings.ToolExecutionError().format(e.message()),
				resolved.failed(e),
				ToolResultStatus.FAILURE
			)
		}
	}
	
	private fun buildToolResult(
		content: String,
		presentation: ToolPresentation,
		status: ToolResultStatus,
	): Result = Result(
		id = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		content = content,
		data = null,
		presentation = presentation,
		status = status
	)
}
