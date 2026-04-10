package io.github.whiteelephant.autotweaker.core.llm

import io.github.whiteelephant.autotweaker.core.Base64
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
sealed class ChatMessage {
    abstract val content: String?
    abstract val createdAt: Long

    data class SystemMessage(
        override val content: String,
        override val createdAt: Long
    ) : ChatMessage()

    data class UserMessage(
        override val content: String,
        override val createdAt: Long,
        val pictures: List<Base64>? = null
    ) : ChatMessage()

    data class AssistantMessage(
        override val content: String?,
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
        override val content: String?,
        override val createdAt: Long,
        val statusCode: HttpStatusCode?,
    ) : ChatMessage()
}
