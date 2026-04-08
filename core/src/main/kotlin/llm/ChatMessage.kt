package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.serialization.Serializable

@Serializable
sealed class ChatMessage {
    abstract val content: String
    abstract val createdAt: Long

    data class SystemMessage(
        override val content: String,
        override val createdAt: Long
    ) : ChatMessage()

    data class UserMessage(
        override val content: String,
        override val createdAt: Long,
        val pictureBase64: String? = null
    ) : ChatMessage()

    data class AssistantMessage(
        override val content: String,
        override val createdAt: Long,
        val reasoningContent: String? = null,
        val toolCalls: List<ToolCall>? = null,
        val model: String
    ) : ChatMessage() {
        data class ToolCall(
            val id: String,
            val name: String,
            val arguments: String
        )
    }

    data class ToolMessage(
        override val content: String,
        override val createdAt: Long,
        val toolCallId: String
    ) : ChatMessage()

    data class ErrorMessage(
        override val content: String,
        override val createdAt: Long,
        val error: Error,
    ) : ChatMessage() {
        sealed class Error {
            data class Message(
                val message: String
            ) : Error()

            data class StatusCode(
                val statusCode: Int
            ) : Error()

            object Unknown : Error()

            companion object {
                fun from(throwable: Throwable) = throwable.message?.let {
                    Message(it)
                } ?: Unknown
            }
        }
    }

}
