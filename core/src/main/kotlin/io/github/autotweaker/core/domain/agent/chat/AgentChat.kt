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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.core.domain.agent.AgentContext
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
			"Agent chat started  agentId={}  model={}  fallbackModels={}  thinking={}  messages={}",
			agentId,
			request.model.model.modelInfo.modelId,
			request.model.fallback?.size,
			request.model.thinking,
			messages.size,
		)
		
		val modelById = buildMap {
			put(request.model.model.id, request.model.model)
			request.model.fallback?.forEach { put(it.id, it) }
		}
		
		val results = ResilientChat.execute(
			model = request.model.model,
			fallbackModels = request.model.fallback,
			messages = messages,
			tools = request.tools,
			stream = true,
			thinking = request.model.thinking,
		)
		
		val errors = mutableListOf<AgentChatStreamResult.Failing.Error>()
		
		results.collect { resilientResult ->
			when (val result = resilientResult.result) {
				is ChatResult.Chunk -> {
					val msg = result.message
					if (msg != null) emit(
						AgentChatStreamResult.Delta(
							StreamDelta(
								content = msg.content,
								reasoningContent = msg.reasoningContent,
								toolCallFragments = result.toolCalls,
							)
						)
					)
				}
				
				is ChatResult.Assembled -> {
					when (val msg = result.message) {
						is ChatMessage.ErrorMessage -> {
							log.debug(
								"Received agent chat error  agentId={}  model={}  statusCode={}  errorCount={}",
								agentId,
								resilientResult.model,
								msg.statusCode,
								errors.size + 1
							)
							errors += AgentChatStreamResult.Failing.Error(
								content = msg.content,
								statusCode = msg.statusCode,
								model = resilientResult.model,
								timestamp = msg.createdAt,
								usage = result.usage,
							)
							emit(AgentChatStreamResult.Failing(errors = errors.toList()))
						}
						
						is ChatMessage.AssistantMessage -> {
							val resultModel = modelById[resilientResult.model] ?: request.model.model
							val snapshot = result.usage?.let { usage ->
								UsageSnapshot(usage, resultModel.modelInfo)
							}
							val assistantMessage = AgentContext.Message.Assistant(
								reasoning = msg.reasoningContent,
								content = msg.content,
								modelId = resilientResult.model,
								timestamp = msg.createdAt,
								usageSnapshot = snapshot,
							)
							emit(
								AgentChatStreamResult.Assembled(
									message = assistantMessage,
									toolCalls = msg.toolCalls,
									finishReason = result.finishReason,
								)
							)
						}
						
						else -> return@collect
					}
				}
			}
		}
	}
}
