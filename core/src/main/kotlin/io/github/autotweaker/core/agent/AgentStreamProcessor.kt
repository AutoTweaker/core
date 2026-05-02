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

import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.agent.llm.agentChat
import kotlinx.coroutines.CancellationException

sealed class StreamProcessResult {
	data object Completed : StreamProcessResult()
	data class ToolCallsRequired(
		val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
	) : StreamProcessResult()
	
	data object Cancelled : StreamProcessResult()
	data class Failed(val message: String) : StreamProcessResult()
}

class AgentStreamProcessor(
	private val emitOutput: suspend (AgentOutput) -> Unit,
	private val onStatusChange: (AgentStatus) -> Unit,
	private val onContextUpdate: suspend (suspend (AgentContext) -> AgentContext) -> Unit,
) {
	suspend fun process(
		request: AgentChatRequest,
	): StreamProcessResult {
		var earlyResult: StreamProcessResult? = null
		
		try {
			agentChat(request).collect { result ->
				when (result) {
					is AgentChatStreamResult.Reasoning -> {
						emitOutput(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.REASONING, result))
					}
					
					is AgentChatStreamResult.Outputting -> {
						emitOutput(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.OUTPUTTING, result))
					}
					
					is AgentChatStreamResult.Failing -> {
						val retrying = result.errors.lastOrNull()?.retrying
						if (retrying != null) {
							onStatusChange(AgentStatus.RETRYING)
							emitOutput(AgentOutput.StreamMessage(AgentOutput.StreamMessage.Status.RETRYING, result))
						} else {
							val errorMessage = result.errors.lastOrNull()?.content ?: "All retries exhausted"
							emitOutput(AgentOutput.Error(errorMessage, AgentOutput.Error.Type.LLM))
							earlyResult = StreamProcessResult.Failed(errorMessage)
							return@collect
						}
					}
					
					is AgentChatStreamResult.Finished -> {
						val resultContext = result.result.context
						val resultToolCalls = result.result.toolCalls
						onContextUpdate { ctx ->
							val updatedRound = ctx.currentRound?.copy(
								assistantMessage = resultContext,
								pendingToolCalls = resultToolCalls,
							)
							ctx.copy(currentRound = updatedRound)
						}
						
						emitOutput(
							AgentOutput.StreamMessage(
								AgentOutput.StreamMessage.Status.FINISHED,
								result,
							)
						)
						
						if (!resultToolCalls.isNullOrEmpty()) {
							emitOutput(AgentOutput.ToolCallRequest(resultToolCalls))
							earlyResult = StreamProcessResult.ToolCallsRequired(resultToolCalls)
						} else {
							earlyResult = StreamProcessResult.Completed
						}
						return@collect
					}
				}
			}
			return earlyResult ?: StreamProcessResult.Completed
		} catch (_: CancellationException) {
			return StreamProcessResult.Cancelled
		} catch (e: Exception) {
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
