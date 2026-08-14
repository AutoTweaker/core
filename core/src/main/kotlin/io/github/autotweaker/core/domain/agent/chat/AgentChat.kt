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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

object AgentChat : Loggable, I18nable {
	fun execute(
		request: AgentChatRequest, agentId: UUID
	): Flow<AgentChatStreamResult> = flow {
		val messages = request.toChatMessages(i18n.getLanguage())
		
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
		
		results.collect {
			when (val result = it.result) {
				is ChatResult.Chunk -> {
					val msg = result.message
					emit(
						AgentChatStreamResult.Delta(
							StreamDelta(
								content = msg?.content,
								reasoningContent = msg?.reasoningContent,
								toolCallFragments = result.toolCalls,
							)
						)
					)
				}
				
				is ChatResult.Assembled -> {
					when (val msg = result.message) {
						is ChatMessage.ErrorMessage -> {
							log.debug(
								"Received agent chat error  agentId={}  model={}  statusCode={}",
								agentId,
								it.model,
								msg.statusCode,
							)
							emit(
								AgentChatStreamResult.Failing(
									error = msg.content,
									statusCode = msg.statusCode,
									model = it.model,
									timestamp = msg.createdAt,
									usage = result.usage,
								)
							)
						}
						
						is ChatMessage.AssistantMessage -> {
							val resultModel = modelById[it.model] ?: request.model.model
							val snapshot = result.usage?.let { usage ->
								UsageSnapshot(usage, resultModel.modelInfo)
							}
							val assistantMessage = RuntimeContext.Message.Assistant(
								id = UUID.randomUUID(),
								timestamp = msg.createdAt,
								reasoning = msg.reasoningContent,
								content = msg.content,
								modelId = it.model,
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
						
						else -> unreachable()
					}
				}
			}
		}
	}
}
