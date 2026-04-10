package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest

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
        // System prompt
        add(ChatMessage.SystemMessage(context.systemPrompt, java.time.Instant.now()))

        // Summarized messages (compacted context)
        if (context.summarizedMessages.isNotEmpty()) {
            add(ChatMessage.SystemMessage(context.summarizedMessages, java.time.Instant.now()))
        }

        // History rounds
        for (round in context.historyRounds) {
            add(round.userMessage.toChatMessage())
            round.turns?.forEach { addTurn(it) }
            round.finalAssistantMessage?.let { add(it.toChatMessage()) }
        }

        // Current round
        add(current.userMessage.toChatMessage())
        current.turns?.forEach { addTurn(it) }
    }

    return ChatRequest(
        model = model.name,
        messages = messages,
        thinking = thinking,
        maxTokens = maxTokens,
        tools = tools,
        temperature = temperature,
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
        add(turn.assistantMessage?.toChatMessage(toolCalls)
            ?: ChatMessage.AssistantMessage(
                content = null,
                createdAt = turn.tools.first().call.timestamp,
                model = turn.tools.first().call.model.name,
                toolCalls = toolCalls,
            ))
    } else {
        turn.assistantMessage?.let { add(it.toChatMessage()) }
    }
    turn.tools.forEach { add(it.toChatMessage()) }
}

private fun AgentContext.Message.User.toChatMessage() = ChatMessage.UserMessage(
    content = content,
    createdAt = timestamp,
    pictures = images,
)

private fun AgentContext.Message.Assistant.toChatMessage(
    toolCalls: List<ChatMessage.AssistantMessage.ToolCall>? = null,
) = ChatMessage.AssistantMessage(
    content = content,
    createdAt = timestamp,
    reasoningContent = reasoning,
    model = model.name,
    toolCalls = toolCalls,
)

private fun AgentContext.Message.Tool.toChatMessage() = ChatMessage.ToolMessage(
    content = result.content,
    createdAt = result.timestamp,
    toolCallId = callId,
)
