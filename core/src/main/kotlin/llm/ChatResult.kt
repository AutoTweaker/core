package io.github.whiteelephant.autotweaker.core.llm

data class ChatResult(
    val message: ChatMessage? = null,
    val finishReason: String? = null,
    val usage: Usage? = null
)
