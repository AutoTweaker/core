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

package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import kotlin.time.Instant

private fun toPendingToolCalls(
	toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
	assistantMessageId: UUID,
	timestamp: Instant,
	model: Model,
): List<AgentContext.CurrentRound.PendingToolCall>? {
	if (toolCalls.isNullOrEmpty()) return null
	return toolCalls.map {
		AgentContext.CurrentRound.PendingToolCall(
			callId = it.id,
			assistantMessageId = assistantMessageId,
			name = it.name,
			arguments = it.arguments,
			timestamp = timestamp,
			model = model,
			reason = null
		)
	}
}

fun agentChat(request: AgentChatRequest): Flow<AgentChatStreamResult> = flow {
	val chatRequest = request.toChatRequest().copy(stream = true)
	
	val results = resilientChat(
		model = request.model,
		fallbackModels = request.fallbackModels,
		request = chatRequest,
	)
	
	var lastRetrying: Model? = null
	val errors = mutableListOf<AgentChatStreamResult.Failing.Error>()
	
	try {
		results.collect { resilientResult ->
			val result = resilientResult.result
			
			if (resilientResult.retrying != null) {
				lastRetrying = resilientResult.retrying
			}
			
			when (result) {
				is ChatResult.Chunk -> {
					val msg = result.message
					if (msg != null) {
						emit(
							AgentChatStreamResult.Delta(
								content = msg.content,
								reasoningContent = msg.reasoningContent,
								toolCallFragments = result.toolCalls,
							)
						)
					}
				}
				
				is ChatResult.Assembled -> {
					val msg = result.message
					if (msg is ChatMessage.ErrorMessage) {
						errors += AgentChatStreamResult.Failing.Error(
							content = msg.content,
							statusCode = msg.statusCode,
							retrying = resilientResult.retrying,
							timestamp = msg.createdAt,
						)
						emit(AgentChatStreamResult.Failing(errors = errors.toList()))
						return@collect
					}
					
					val assistantMsg = msg as? ChatMessage.AssistantMessage ?: return@collect
					val resultModel = lastRetrying ?: request.model
					val assistantMessage = AgentContext.Message.Assistant(
						reasoning = assistantMsg.reasoningContent,
						content = assistantMsg.content,
						model = resultModel,
						timestamp = assistantMsg.createdAt,
						usage = result.usage,
					)
					emit(
						AgentChatStreamResult.Assembled(
							message = assistantMessage,
							toolCalls = toPendingToolCalls(
								assistantMsg.toolCalls,
								assistantMessage.id,
								assistantMsg.createdAt,
								resultModel
							),
							finishReason = result.finishReason,
						)
					)
				}
			}
		}
	} catch (_: IllegalStateException) {
		//所有候选模型耗尽
		return@flow
	}
}
