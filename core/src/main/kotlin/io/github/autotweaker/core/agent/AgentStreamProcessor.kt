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

import io.github.autotweaker.core.agent.llm.AgentChat
import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.*

sealed class StreamProcessResult {
	data object Completed : StreamProcessResult()
	data class ToolCallsRequired(
		val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
	) : StreamProcessResult()
	
	data object Cancelled : StreamProcessResult()
	data class Failed(val message: String) : StreamProcessResult()
}

class AgentStreamProcessor(
	private val agentId: UUID,
	private val emitOutput: suspend (AgentOutput) -> Unit,
	private val onStatusChange: (AgentStatus) -> Unit,
	private val onContextUpdate: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun process(
		request: AgentChatRequest,
	): StreamProcessResult {
		var earlyResult: StreamProcessResult? = null
		
		try {
			AgentChat.execute(request, agentId).collect { result ->
				when (result) {
					is AgentChatStreamResult.Delta -> {
						emitOutput(AgentOutput.StreamDelta(result))
					}
					
					is AgentChatStreamResult.Failing -> {
						val lastError = result.errors.lastOrNull()
						if (lastError?.retrying != null) {
							logger.debug(
								"LLM stream error occurred  retry initiated  agentId={}  model={}  error={}",
								agentId, lastError.retrying.modelInfo.id, lastError.content
							)
							onStatusChange(AgentStatus.RETRYING)
							emitOutput(AgentOutput.StreamError(lastError))
						} else {
							val errorMessage = lastError?.content ?: "All retries exhausted"
							logger.warn(
								"LLM stream failed  retries exhausted  agentId={}  error={}", agentId, errorMessage
							)
							emitOutput(AgentOutput.Error(errorMessage, AgentOutput.Error.Type.LLM))
							earlyResult = StreamProcessResult.Failed(errorMessage)
							return@collect
						}
					}
					
					is AgentChatStreamResult.Assembled -> {
						val resultMessage = result.message
						val resultToolCalls = result.toolCalls
						onContextUpdate { ctx ->
							val updatedRound = ctx.currentRound?.copy(
								assistantMessage = resultMessage,
								pendingToolCalls = resultToolCalls,
							)
							ctx.copy(currentRound = updatedRound)
						}
						
						if (!resultToolCalls.isNullOrEmpty()) {
							logger.debug(
								"LLM stream completed with tool calls  agentId={}  toolCallCount={}",
								agentId, resultToolCalls.size
							)
							emitOutput(AgentOutput.ToolCallRequest(resultToolCalls))
							earlyResult = StreamProcessResult.ToolCallsRequired(resultToolCalls)
						} else {
							logger.debug("LLM stream completed  agentId={}", agentId)
							earlyResult = StreamProcessResult.Completed
						}
						return@collect
					}
				}
			}
			return earlyResult ?: StreamProcessResult.Completed
		} catch (_: CancellationException) {
			logger.debug("LLM stream cancelled  agentId={}", agentId)
			return StreamProcessResult.Cancelled
		} catch (e: Exception) {
			logger.error("LLM stream process failed  agentId={}", agentId, e)
			val message = buildString {
				append(e::class.simpleName ?: e::class.qualifiedName ?: "UnknownException")
				e.message?.let { append(": ").append(it) }
				val cause = e.cause
				if (cause != null) append(" (caused by ").append(
					cause::class.simpleName ?: cause::class.qualifiedName
				).append(")")
			}
			emitOutput(AgentOutput.Error(message, AgentOutput.Error.Type.LLM))
			return StreamProcessResult.Failed(message)
		}
	}
}
