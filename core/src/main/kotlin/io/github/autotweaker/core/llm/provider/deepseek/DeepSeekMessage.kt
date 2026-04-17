package io.github.autotweaker.core.llm.provider.deepseek

import io.github.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = DeepSeekMessageSerializer::class)
sealed class DeepSeekMessage {
    abstract val role: String
    abstract val content: String?

    @Serializable
    data class SystemMessage(
        override val role: String = "system",
        override val content: String,
        val name: String? = null
    ) : DeepSeekMessage()

    @Serializable
    data class UserMessage(
        override val role: String = "user",
        override val content: String,
        val name: String? = null
    ) : DeepSeekMessage()

    @Serializable
    data class AssistantMessage(
        override val role: String = "assistant",
        override val content: String?,
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
        override val content: String,
        @SerialName("tool_call_id")
        val toolCallId: String
    ) : DeepSeekMessage()
}

object DeepSeekMessageSerializer : JsonContentPolymorphicSerializer<DeepSeekMessage>(DeepSeekMessage::class) {
    override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement) = when {
        "tool_call_id" in element.jsonObject -> DeepSeekMessage.ToolMessage.serializer()
        element.jsonObject["role"]?.jsonPrimitive?.content == "assistant" -> DeepSeekMessage.AssistantMessage.serializer()
        element.jsonObject["role"]?.jsonPrimitive?.content == "system" -> DeepSeekMessage.SystemMessage.serializer()
        else -> DeepSeekMessage.UserMessage.serializer()
    }
}
