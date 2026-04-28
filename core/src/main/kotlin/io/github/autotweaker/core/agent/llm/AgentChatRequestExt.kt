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
			add(round.finalAssistantMessage.toChatMessage())
		}
		
		//当前轮次
		add(current.userMessage.toChatMessage())
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

private fun AgentContext.Message.User.toChatMessage() = ChatMessage.UserMessage(
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
	model = model.name,
	toolCalls = toolCalls,
)

private fun AgentContext.Message.Tool.toChatMessage() = ChatMessage.ToolMessage(
	content = result.content,
	createdAt = result.timestamp,
	toolCallId = callId,
)
