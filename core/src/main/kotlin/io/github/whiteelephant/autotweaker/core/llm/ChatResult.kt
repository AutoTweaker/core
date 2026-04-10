package io.github.whiteelephant.autotweaker.core.llm

data class ChatResult(
    val message: ChatMessage? = null,
    val finishReason: FinishReason? = null,
    val usage: Usage? = null
) {
    data class FinishReason(
        val reason: String,
        val type: Type
    ) {
        enum class Type {
            STOP, TOOL, ERROR, FILTER, LENGTH
        }
    }
}
