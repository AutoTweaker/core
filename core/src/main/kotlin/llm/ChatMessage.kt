package io.github.whiteelephant.autotweaker.core.llm


sealed class ChatMessage {
    abstract val content: String
    abstract val createdAt: Long

    data class SystemMessage(
        override val content: String,
        override val createdAt: Long
    ) : ChatMessage()

    data class UserMessage(
        override val content: String,
        override val createdAt: Long
    ) : ChatMessage()

    data class AssistantMessage(
        override val content: String,
        override val createdAt: Long,
        val reasoningContent: String? = null,
        val model: String
    ) : ChatMessage()

    data class ToolMessage(
        override val content: String,
        override val createdAt: Long,
        val toolId: String
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
