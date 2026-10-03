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

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.AgentOutput
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.core.domain.agent.MessageBuilder
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.chat.ResilientChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import java.util.*

class AgentChat(
	private val chat: ResilientChat,
	private val msg: MessageBuilder
) : Loggable, I18nable {
	fun execute(
		request: AgentChatRequest, agentId: UUID
	): Flow<AgentChatResult> = flow {
		val messages = request.context.toChatMessages()
		
		log.debug(
			"Agent chat started  agentId={}  model={}  fallbackModels={}  reasoning={}  messages={}",
			agentId,
			request.model.model.modelInfo.modelId,
			request.model.fallback?.size,
			request.model.reasoning,
			messages.size,
		)
		
		val results = chat.execute(
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
					AgentChatResult.Delta(
						AgentOutput.LlmDelta(
							content = result.content,
							reasoningContent = result.reasoningContent,
							toolCallFragments = result.toolCalls,
						)
					)
				)
				
				is ChatResult.Failed -> emit(
					AgentChatResult.Failing(
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
				
				
				is ChatResult.Assembled -> emit(
					AgentChatResult.Assembled(
						message = msg.assistant(
							reasoning = result.message.reasoningContent,
							content = result.message.content,
							model = it.model,
							usage = result.usage
						),
						toolCalls = result.message.toolCalls,
					)
				)
			}
		}
	}
	
	private val placeholder by lazy { CorruptedPlaceholder().get() }
	
	fun RuntimeContext.toChatMessages(): List<ChatMessage> = buildList {
		buildList {
			historyRounds?.let { addAll(it) }
			currentRound?.let { add(it) }
		}.ifEmpty { error("No round to send request") }.forEach { round ->
			add(round.userMessage())
			round.turns?.forEach { addTurn(it) }
			round.assistantMessage()?.let { add(it) }
		}
	}.inject(
		injections, compactedRounds?.summaryMessage?.fold(
			onIntact = { it.content },
			onCorrupted = { placeholder }
		)
	)
	
	private fun AgentContextIndex.Round.userMessage() = userMessage.fold(
		onIntact = {
			ChatMessage.User(
				it.content.injectContext(
					it.timestamp,
					TimeZone.currentSystemDefault(),
					i18n.getLanguage()
				).inject(),
			)
		},
		onCorrupted = { ChatMessage.User(placeholder.toContentPart()) }
	)
	
	private fun MutableList<ChatMessage>.addTurn(turn: AgentContextIndex.Turn) {
		val calls = mutableListOf<ChatMessage.Assistant.ToolCall>()
		val results = mutableListOf<ChatMessage.ToolResult>()
		turn.tools.forEach { tool ->
			var callId: String? = null
			calls += tool.call.fold(
				onIntact = {
					callId = it.callId
					ChatMessage.Assistant.ToolCall(
						id = it.callId,
						name = it.callName,
						arguments = it.arguments
					)
				},
				onCorrupted = {
					callId = it.id.toString()
					ChatMessage.Assistant.ToolCall(
						id = it.id.toString(),
						name = "unknown",
						arguments = placeholder
					)
				}
			)
			results += tool.result.fold(
				onIntact = {
					ChatMessage.ToolResult(
						id = it.callId,
						content = it.content
					)
				},
				onCorrupted = {
					ChatMessage.ToolResult(
						id = callId ?: unreachable(),
						content = placeholder
					)
				}
			)
		}
		
		this += turn.assistantMessage.fold(
			onIntact = {
				ChatMessage.Assistant(
					content = it.content,
					reasoningContent = it.reasoning,
					toolCalls = calls
				)
			},
			onCorrupted = {
				ChatMessage.Assistant(
					content = placeholder,
					toolCalls = calls
				)
			}
		)
		addAll(results)
	}
	
	private fun AgentContextIndex.Round.assistantMessage() = assistantMessage?.fold(
		onIntact = {
			ChatMessage.Assistant(
				content = it.content,
				reasoningContent = it.reasoning
			)
		},
		onCorrupted = {
			ChatMessage.Assistant(
				content = placeholder
			)
		}
	)
	
	@AutoService(SettingDef::class)
	class CorruptedPlaceholder : StringSetting(
		"[损坏的消息]",
		zh("上下文中的消息无法从硬盘中加载时的占位内容")
	)
}
