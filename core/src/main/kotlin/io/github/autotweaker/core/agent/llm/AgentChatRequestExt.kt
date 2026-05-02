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
import io.github.autotweaker.core.llm.ChatRequest

fun AgentChatRequest.toChatRequest(): ChatRequest {
	val current = context.currentRound
		?: throw IllegalStateException("No current round available")
	
	val lastMessage = when {
		current.assistantMessage != null -> current.assistantMessage
		current.turns?.isNotEmpty() == true -> {
			val lastTurn = current.turns.last()
			lastTurn.tools.lastOrNull() ?: lastTurn.assistantMessage
		}
		
		else -> null
	}
	
	if (lastMessage is AgentContext.Message.Assistant) {
		throw IllegalStateException("Last message is an assistant message, cannot send request")
	}
	
	if (current.pendingToolCalls != null) {
		throw IllegalStateException("Pending tool calls exist, cannot send request")
	}
	
	val messages = buildList {
		//系统提示
		context.systemPrompt?.let {
			add(ChatMessage.SystemMessage(it, kotlin.time.Clock.System.now()))
		}
		
		//历史轮次
		for (round in context.historyRounds.orEmpty()) {
			add(round.userMessage.toChatMessage())
			round.turns?.forEach { addTurn(it) }
			round.finalAssistantMessage?.let { add(it.toChatMessage()) }
		}
		
		//当前轮次
		add(current.userMessage.toChatMessage(context.summarizedMessage))
		current.turns?.forEach { addTurn(it) }
	}
	
	return ChatRequest(
		model = model.name,
		messages = messages,
		thinking = thinking,
		tools = tools,
	)
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

private fun AgentContext.Message.User.toChatMessage(summarizedMessage: String? = null) = ChatMessage.UserMessage(
	content = buildString {
		appendLine("<time>$timestamp</time>")
		if (summarizedMessage != null) {
			appendLine("<summary>")
			appendLine(summarizedMessage)
			appendLine("</summary>")
		}
		append(content ?: "")
	}.trimEnd(),
	createdAt = timestamp,
	pictures = images,
)

private fun AgentContext.Message.Assistant.toChatMessage(
	toolCalls: List<ChatMessage.AssistantMessage.ToolCall>? = null,
) = ChatMessage.AssistantMessage(
	content = content ?: "",
	createdAt = timestamp,
	reasoningContent = reasoning ?: "",
	toolCalls = toolCalls,
)

private fun AgentContext.Message.Tool.toChatMessage() = ChatMessage.ToolMessage(
	content = result.content,
	createdAt = result.timestamp,
	toolCallId = callId,
)
