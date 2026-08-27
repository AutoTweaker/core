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

import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.chat.MessageConverts.inject
import io.github.autotweaker.core.domain.agent.chat.MessageConverts.injectContext
import kotlinx.datetime.TimeZone

fun AgentChatRequest.toChatMessages(): List<ChatMessage> {
	val current = checkNotNull(context.currentRound) { "No current round available" }
	
	val lastMessage = when {
		current.assistantMessage != null -> current.assistantMessage
		current.turns?.isNotEmpty() == true -> {
			val lastTurn = current.turns.last()
			lastTurn.tools.lastOrNull() ?: lastTurn.assistantMessage
		}
		
		else -> null
	}
	
	check(lastMessage !is RuntimeContext.Message.Assistant)
	{ "Last message is an assistant message, cannot send request" }
	check(current.pendingToolCalls == null)
	{ "Pending tool calls exist, cannot send request" }
	
	return buildList {
		//历史轮次
		for (round in context.historyRounds.orEmpty()) {
			addAll(round.toChatMessages())
		}
		
		//当前轮次
		add(current.userMessage.toChatMessage())
		current.turns?.forEach { addTurn(it) }
	}.inject(context.injections, context.compactedRounds?.summarizedMessage?.content)
}

fun RuntimeContext.CompletedRound.toChatMessages(): List<ChatMessage> = buildList {
	add(userMessage.toChatMessage())
	turns?.forEach { addTurn(it) }
	finalAssistantMessage?.let { add(it.toChatMessage()) }
}

private fun MutableList<ChatMessage>.addTurn(turn: RuntimeContext.Turn) {
	val toolCalls = turn.tools.orNull()?.map { tool ->
		ChatMessage.Assistant.ToolCall(
			id = tool.callId,
			name = tool.call.callName,
			arguments = tool.call.arguments,
		)
	}
	
	add(turn.assistantMessage.toChatMessage(toolCalls))
	
	turn.tools.forEach { add(it.toChatMessage()) }
}

private fun RuntimeContext.Message.User.toChatMessage() = content
	.injectContext(timestamp, TimeZone.currentSystemDefault())
	.inject()
	.let { content ->
		ChatMessage.User(
			content = content,
			timestamp = timestamp,
		)
	}

private fun RuntimeContext.Message.Assistant.toChatMessage(
	toolCalls: List<ChatMessage.Assistant.ToolCall>? = null,
) = ChatMessage.Assistant(
	content = content,
	timestamp = timestamp,
	reasoningContent = reasoning,
	toolCalls = toolCalls,
)

private fun RuntimeContext.Message.Tool.toChatMessage() = ChatMessage.ToolResult(
	content = result.content,
	timestamp = result.timestamp,
	toolCallId = callId,
)
