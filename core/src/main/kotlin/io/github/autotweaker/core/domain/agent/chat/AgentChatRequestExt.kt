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

import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.core.domain.agent.AgentContext
import kotlin.time.Clock

fun AgentChatRequest.toChatMessages(): List<ChatMessage> {
	val current = context.currentRound ?: throw IllegalStateException("No current round available")
	
	val lastMessage = when {
		current.assistantMessage != null -> current.assistantMessage
		current.turns?.isNotEmpty() == true -> {
			val lastTurn = current.turns.last()
			lastTurn.tools.lastOrNull() ?: lastTurn.assistantMessage
		}
		
		else -> null
	}
	
	check(lastMessage !is AgentContext.Message.Assistant) { "Last message is an assistant message, cannot send request" }
	check(current.pendingToolCalls == null) { "Pending tool calls exist, cannot send request" }
	
	return buildList {
		//系统提示
		context.systemPrompt?.let {
			add(ChatMessage.SystemMessage(it, Clock.System.now()))
		}
		
		//历史轮次
		for (round in context.historyRounds.orEmpty()) {
			addAll(round.toChatMessages())
		}
		
		//当前轮次
		add(current.userMessage.toChatMessage())
		current.turns?.forEach { addTurn(it) }
	}.inject(context.injections, context.summarizedMessage?.content)
}

fun AgentContext.CompletedRound.toChatMessages(): List<ChatMessage> = buildList {
	add(userMessage.toChatMessage())
	turns?.forEach { addTurn(it) }
	finalAssistantMessage?.let { add(it.toChatMessage()) }
}

private fun MutableList<ChatMessage>.addTurn(turn: AgentContext.Turn) {
	val toolCalls = turn.tools.map { tool ->
		ChatMessage.AssistantMessage.ToolCall(
			id = tool.callId,
			name = tool.name,
			arguments = tool.call.arguments,
		)
	}
	if (toolCalls.isNotEmpty()) {
		add(turn.assistantMessage.toChatMessage(toolCalls))
	} else {
		add(turn.assistantMessage.toChatMessage())
	}
	turn.tools.forEach { add(it.toChatMessage()) }
}

private fun AgentContext.Message.User.toChatMessage() = content
	.injectTimestamp(timestamp)
	.inject()
	.let { text ->
		ChatMessage.UserMessage(
			content = text,
			createdAt = timestamp,
			pictures = content.images,
		)
	}

private fun AgentContext.Message.Assistant.toChatMessage(
	toolCalls: List<ChatMessage.AssistantMessage.ToolCall>? = null,
) = ChatMessage.AssistantMessage(
	content = content,
	createdAt = timestamp,
	reasoningContent = reasoning,
	toolCalls = toolCalls,
)

private fun AgentContext.Message.Tool.toChatMessage() = ChatMessage.ToolMessage(
	content = result.content,
	createdAt = result.timestamp,
	toolCallId = callId,
)
