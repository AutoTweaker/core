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
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.exception.I18nableException
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.*
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ToolCallingStage(
	private val agentId: UUID,
	private val tools: Tools,
	private val msg: MessageBuilder,
	private val provider: ToolProvider,
	private val workspace: () -> Path,
	private val truncation: TruncationService,
	private val status: MutableStateFlow<AgentStatus>,
	private val onOutput: (RuntimeOutput) -> Unit,
	private val onToolCall: (Pair<String, List<UiBlock>>?) -> Unit
) : Loggable, Traceable {
	@Volatile
	private var toolJob: Job? = null
	
	suspend fun cancelToolJob() {
		toolJob?.cancelAndJoin()
		toolJob = null
	}
	
	suspend fun execute(
		toolCall: AgentToolCallImpl,
		model: AgentModel,
		context: RuntimeContext,
	) {
		val call = toolCall.call
		val resolved = toolCall.resolved!!
		suspend fun toolResult(
			content: String,
			presentation: ToolPresentation,
			status: ToolResultStatus,
		) = msg.toolResult(
			callId = call.callId,
			content = content,
			data = null,
			presentation = presentation,
			status = status
		)
		
		val timeoutSeconds = ToolSettings.TimeoutSeconds().get()
		val startTime = TimeSource.Monotonic.markNow()
		val result = trace.catching {
			coroutineScope {
				toolJob = coroutineContext[Job]
				toolCall.calling()
				onToolCall(call.callId to resolved.executing())
				status.value = AgentStatus.TOOL_CALLING
				withTimeout(timeoutSeconds.seconds) {
					val provider = provider.build(
						workspace = workspace,
						onOutput = onOutput,
						model = model,
						context = context,
						truncation = truncation,
					)
					
					tools.executeTool(
						toolName = call.validatedToolName ?: unreachable(),
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
			withContext(NonCancellable) {
				toolJob = null
				onToolCall(null)
				status.value = AgentStatus.PROCESSING
			}
		}.ensureActive()
			.recoverException { _: TimeoutCancellationException ->
				val elapsed = startTime.elapsedNow()
				log.warn(
					"Failed tool execution  agentId={}  tool={}  reason=TIMEOUT  elapsed={}",
					agentId, call.validatedToolName, elapsed
				)
				toolResult(
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
				toolResult(
					ToolSettings.CancelledExecuting().get(),
					resolved.cancelled(),
					ToolResultStatus.CANCELLED
				)
			}.getOrElse { e ->
				if (e is I18nableException)
					log.warn(
						"Failed tool execution  agentId={}  tool={}  exception={}  reason={}",
						agentId, call.validatedToolName, e::class.simpleName, e.message
					)
				else log.error(
					"Failed tool execution  agentId={}  tool={}", agentId, call.validatedToolName, e
				)
				toolResult(
					ToolSettings.ToolExecutionError().format(e.message()),
					resolved.failed(e),
					ToolResultStatus.FAILURE
				)
			}
		
		toolCall.finish(result)
	}
}
