package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
sealed class DeepSeekMessage {
    abstract val role: String

    @Serializable
    data class SystemMessage(
        override val role: String = "system",
        val content: String,
        val name: String? = null
    ) : DeepSeekMessage()

    @Serializable
    data class UserMessage(
        override val role: String = "user",
        val content: String,
        val name: String? = null
    ) : DeepSeekMessage()

    @Serializable
    data class AssistantMessage(
        override val role: String = "assistant",
        val content: String?,
        @SerialName("reasoning_content")
        val reasoningContent: String? = null,
        val name: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<ToolCall>? = null
    ) : DeepSeekMessage() {
        @Serializable
        data class ToolCall(
            val id: String,
            val type: String = "function",
            val function: Function
        ) {
            @Serializable
            data class Function(
                val name: String,
                val arguments: String
            )
        }
    }

    @Serializable
    data class ToolMessage(
        override val role: String = "tool",
        val content: String,
        @SerialName("tool_call_id")
        val toolCallId: String
    ) : DeepSeekMessage()
}
