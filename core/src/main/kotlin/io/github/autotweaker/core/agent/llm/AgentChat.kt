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
import io.github.autotweaker.core.llm.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

private fun toPendingToolCalls(
	toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
	timestamp: Instant,
	model: Model,
): List<AgentContext.CurrentRound.PendingToolCall>? {
	if (toolCalls.isNullOrEmpty()) return null
	return toolCalls.map {
		AgentContext.CurrentRound.PendingToolCall(
			callId = it.id,
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
	
	var reasoningContent = ""
	var content = ""
	var lastMessage: ChatMessage.AssistantMessage? = null
	var lastFinishReason: ChatResult.FinishReason? = null
	var lastUsage: Usage? = null
	var lastRetrying: Model? = null
	val errors = mutableListOf<AgentChatStreamResult.Failing.Error>()
	
	try {
		results.collect { resilientResult ->
			val result = resilientResult.result
			val msg = result.message
			
			if (resilientResult.retrying != null) {
				lastRetrying = resilientResult.retrying
			}
			
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
			
			lastMessage = if (assistantMsg.toolCalls.isNullOrEmpty() && !lastMessage?.toolCalls.isNullOrEmpty()) {
				assistantMsg.copy(toolCalls = lastMessage?.toolCalls)
			} else {
				assistantMsg
			}
			
			if (!assistantMsg.reasoningContent.isNullOrEmpty()) {
				reasoningContent += assistantMsg.reasoningContent
				emit(AgentChatStreamResult.Reasoning(reasoningContent))
			}
			
			if (!assistantMsg.content.isNullOrEmpty()) {
				content += assistantMsg.content
				emit(
					AgentChatStreamResult.Outputting(
						reasoningContent = reasoningContent.ifEmpty { null },
						content = content,
					)
				)
			}
			
			result.finishReason?.let { lastFinishReason = it }
			result.usage?.let { lastUsage = it }
		}
	} catch (_: IllegalStateException) {
		//所有候选模型耗尽
		return@flow
	}
	
	val msg = lastMessage
	val resultModel = lastRetrying ?: request.model
	
	emit(
		AgentChatStreamResult.Finished(
			result = AgentChatStreamResult.Finished.Result(
				context = AgentContext.Message.Assistant(
					reasoning = msg?.reasoningContent ?: reasoningContent.ifEmpty { null },
					content = msg?.content ?: content.ifEmpty { null },
					model = resultModel,
					timestamp = msg?.createdAt ?: Clock.System.now(),
					usage = lastUsage,
				),
				toolCalls = toPendingToolCalls(msg?.toolCalls, msg?.createdAt ?: Clock.System.now(), resultModel),
				finishReason = lastFinishReason,
			)
		)
	)
}
