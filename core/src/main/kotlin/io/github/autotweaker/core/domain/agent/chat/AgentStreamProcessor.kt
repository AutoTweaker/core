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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.*

object AgentStreamProcessor {
	sealed class StreamProcessResult {
		data object Completed : StreamProcessResult()
		data class ToolCallsRequired(
			val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
		) : StreamProcessResult()
		
		data object Cancelled : StreamProcessResult()
		data class Failed(val message: String) : StreamProcessResult()
	}
	
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	suspend fun processRequest(
		request: AgentChatRequest,
		agentId: UUID,
		onContextUpdate: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
		onOutput: suspend (AgentOutput) -> Unit,
	): StreamProcessResult {
		var streamResult: StreamProcessResult? = null
		try {
			AgentChat.execute(request, agentId).collect { result ->
				when (result) {
					is AgentChatStreamResult.Delta -> {
						onOutput(AgentOutput.LlmDelta(result.delta))
					}
					
					is AgentChatStreamResult.Failing -> {
						val lastError = result.errors.lastOrNull() ?: return@collect
						logger.debug(
							"LLM stream failed  agentId={}  model={}  error={}",
							agentId,
							lastError.model,
							lastError.content
						)
						onOutput(AgentOutput.LlmError(lastError))
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
								agentId,
								resultToolCalls.size
							)
							streamResult = StreamProcessResult.ToolCallsRequired(resultToolCalls)
						} else {
							logger.info("LLM stream completed  agentId={}", agentId)
							streamResult = StreamProcessResult.Completed
						}
					}
				}
			}
			
			return streamResult ?: StreamProcessResult.Failed("LLM stream ended without result")
		} catch (_: CancellationException) {
			logger.debug("LLM stream cancelled  agentId={}", agentId)
			return StreamProcessResult.Cancelled
		} catch (e: Exception) {
			logger.error("Failed to process LLM stream  agentId={}", agentId, e)
			val message = buildString {
				append(e::class.simpleName ?: e::class.qualifiedName ?: "UnknownException")
				e.message?.let { append(": ").append(it) }
				val cause = e.cause
				if (cause != null) append(" (caused by ").append(
					cause::class.simpleName ?: cause::class.qualifiedName
				).append(")")
			}
			onOutput(AgentOutput.Error(AgentError(message, AgentError.Type.LLM)))
			return StreamProcessResult.Failed(message)
		}
	}
}
