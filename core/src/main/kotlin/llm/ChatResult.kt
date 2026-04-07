package io.github.whiteelephant.autotweaker.core.llm

data class ChatResult(
    val message: ChatMessage.AssistantMessage? = null,
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val usage: Usage? = null
)
