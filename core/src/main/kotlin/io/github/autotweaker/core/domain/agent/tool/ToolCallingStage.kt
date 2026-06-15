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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class ToolCallingStage(
	private val agentId: UUID,
	private val tools: Tools,
	private val service: SettingService,
	private val onOutput: suspend (AgentOutput) -> Unit,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	
	@Volatile
	private var toolJob: Job? = null
	
	fun cancelToolJob() {
		toolJob?.cancel()
		toolJob = null
	}
	
	suspend fun execute(
		call: ThinkingStage.ResolvedToolCall,
		workspace: WorkspaceMeta,
		containerConfig: ContainerConfig,
		summarizeModel: Model,
		fallbackModels: List<Model>?,
		context: AgentContext,
	): AgentContext.Message.Tool.Result {
		val timeoutSeconds = service.get(AgentToolSettings.TimeoutSeconds()).value
		val timeoutMessage = service.get(AgentToolSettings.TimeoutMessage()).value
		val cancelledMessage = service.get(AgentToolSettings.Cancelled()).value
		
		val startTime = TimeSource.Monotonic.markNow()
		return try {
			coroutineScope {
				toolJob = coroutineContext[Job]
				withTimeout((timeoutSeconds * 1000L).milliseconds) {
					val provider = ToolProvider.buildToolProvider(
						workspace = workspace,
						containerConfig = containerConfig,
						onOutput = onOutput,
						summarizeModel = summarizeModel,
						fallbackModels = fallbackModels,
						context = context
					)
					
					tools.executeTool(
						toolName = call.validated.toolName,
						callId = call.pendingCall.callId,
						arguments = call.validated.args,
						provider = provider,
						onToolOutput = onOutput
					).also {
						logger.info(
							"Called tool  agentId={}  tool={}  status={}",
							agentId, call.validated.toolName, it.status
						)
					}
				}
			}
		} catch (e: TimeoutCancellationException) {
			trace.exception(e)
			val elapsed = startTime.elapsedNow().inWholeSeconds
			logger.warn(
				"Failed tool execution  agentId={}  tool={}  reason=TIMEOUT  elapsed={}s",
				agentId, call.pendingCall.name, elapsed
			)
			buildToolResult(
				timeoutMessage.format(elapsed),
				ToolResultStatus.TIMEOUT
			)
		} catch (e: CancellationException) {
			trace.exception(e)
			logger.debug("Failed tool execution  agentId={}  tool={}  reason=CANCELLED", agentId, call.pendingCall.name)
			buildToolResult(cancelledMessage, ToolResultStatus.CANCELLED)
		} catch (e: Exception) {
			trace.exception(e)
			logger.error("Failed tool execution  agentId={}  tool={}", agentId, call.pendingCall.name, e)
			buildToolResult(e.message ?: "Tool execution failed", ToolResultStatus.FAILURE)
		} finally {
			toolJob = null
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
