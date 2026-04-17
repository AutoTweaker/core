package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Base64
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed class ChatMessage {
    abstract val content: String?
    abstract val createdAt: Instant

    data class SystemMessage(
        override val content: String,
        override val createdAt: Instant
    ) : ChatMessage()

    data class UserMessage(
        override val content: String,
        override val createdAt: Instant,
        val pictures: List<Base64>? = null
    ) : ChatMessage()

    data class AssistantMessage(
        override val content: String?,
        override val createdAt: Instant,
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
        override val createdAt: Instant,
        val toolCallId: String
    ) : ChatMessage()

    data class ErrorMessage(
        override val content: String?,
        override val createdAt: Instant,
        val statusCode: HttpStatusCode?,
    ) : ChatMessage()
}
