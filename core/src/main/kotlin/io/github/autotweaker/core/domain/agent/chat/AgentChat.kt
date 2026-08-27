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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.agent.AgentOutput
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

object AgentChat : Loggable {
	fun execute(
		request: AgentChatRequest, agentId: UUID
	): Flow<AgentChatStreamResult> = flow {
		val messages = request.toChatMessages()
		
		log.debug(
			"Agent chat started  agentId={}  model={}  fallbackModels={}  reasoning={}  messages={}",
			agentId,
			request.model.model.modelInfo.modelId,
			request.model.fallback?.size,
			request.model.reasoning,
			messages.size,
		)
		
		val results = ResilientChat.execute(
			model = request.model.model,
			fallbackModels = request.model.fallback,
			instructions = request.context.systemPrompt,
			messages = messages,
			tools = request.tools,
			stream = true,
			reasoning = request.model.reasoning
		)
		
		results.collect {
			when (val result = it.result) {
				is ChatResult.Chunk -> emit(
					AgentChatStreamResult.Delta(
						AgentOutput.LlmDelta(
							content = result.content,
							reasoningContent = result.reasoningContent,
							toolCallFragments = result.toolCalls,
						)
					)
				)
				
				is ChatResult.Failed -> emit(
					AgentChatStreamResult.Failing(
						error = result.message,
						statusCode = result.statusCode,
						exception = result.exception,
						model = it.model,
					)
				).andLog(log) { _ ->
					debug(
						"Received agent chat error  agentId={}  model={}  statusCode={}",
						agentId,
						it.model,
						result.statusCode,
					)
				}
				
				
				is ChatResult.Assembled -> {
					val msg = result.message
					val assistantMessage = RuntimeContext.Message.Assistant(
						id = UUID(),
						timestamp = msg.timestamp,
						reasoning = msg.reasoningContent,
						content = msg.content,
						modelId = it.model,
						usage = result.usage
					)
					emit(
						AgentChatStreamResult.Assembled(
							message = assistantMessage,
							toolCalls = msg.toolCalls,
						)
					)
				}
			}
		}
	}
}
