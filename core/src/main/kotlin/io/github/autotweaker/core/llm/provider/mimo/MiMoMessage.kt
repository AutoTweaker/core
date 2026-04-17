package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.base.openai.*
import io.github.autotweaker.core.llm.ChatRequest.Tool.Parameters

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = MiMoMessageSerializer::class)
sealed class MiMoMessage {
    abstract val role: String
    abstract val content: List<Content>?

    @Serializable
    sealed class Content {
        @Serializable
        @SerialName("text")
        data class TextPart(
            val text: String
        ) : Content()

        @Serializable
        @SerialName("image_url")
        data class ImagePart(
            @SerialName("image_url")
            val imageUrl: Url
        ) : Content() {
            @Serializable
            data class Url(
                val url: String
            )
        }

        @Serializable
        @SerialName("input_audio")
        data class AudioPart(
            @SerialName("input_audio")
            val inputAudio: Data
        ) : Content() {
            @Serializable
            data class Data(
                val data: String
            )
        }

        @Serializable
        @SerialName("video_url")
        data class VideoPart(
            @SerialName("video_url")
            val videoUrl: Url,
            val fps: Int? = null,
            @SerialName("media_resolution")
            val mediaResolution: MediaResolution? = MediaResolution.DEFAULT
        ) : Content() {
            @Serializable
            data class Url(
                val url: String
            )

            @Serializable
            enum class MediaResolution {
                @SerialName("default")
                DEFAULT,

                @SerialName("max")
                MAX
            }
        }
    }

    @Serializable
    data class DeveloperMessage(
        override val role: String = "system",
        override val content: List<Content>,
        val name: String? = null
    ) : MiMoMessage()

    @Serializable
    data class SystemMessage(
        override val role: String = "system",
        override val content: List<Content>,
        val name: String? = null
    ) : MiMoMessage()

    @Serializable
    data class UserMessage(
        override val role: String = "user",
        override val content: List<Content>,
        val name: String? = null
    ) : MiMoMessage()

    @Serializable
    data class AssistantMessage(
        override val role: String = "assistant",
        override val content: List<Content>?,
        @SerialName("reasoning_content")
        val reasoningContent: String? = null,
        val name: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<MiMoToolCall>? = null
    ) : MiMoMessage()

    @Serializable
    data class ToolMessage(
        override val role: String = "tool",
        override val content: List<Content>,
        @SerialName("tool_call_id")
        val toolCallId: String
    ) : MiMoMessage()
}

object MiMoMessageSerializer : JsonContentPolymorphicSerializer<MiMoMessage>(MiMoMessage::class) {
    override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement) = when {
        "tool_call_id" in element.jsonObject -> MiMoMessage.ToolMessage.serializer()
        element.jsonObject["role"]?.jsonPrimitive?.content == "assistant" -> MiMoMessage.AssistantMessage.serializer()
        element.jsonObject["role"]?.jsonPrimitive?.content == "system" -> MiMoMessage.SystemMessage.serializer()
        element.jsonObject["role"]?.jsonPrimitive?.content == "developer" -> MiMoMessage.DeveloperMessage.serializer()
        else -> MiMoMessage.UserMessage.serializer()
    }
}
