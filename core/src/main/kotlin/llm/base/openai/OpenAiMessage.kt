package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind

import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed class OpenAiMessage {
    abstract val role: String

    @Serializable
    open class OpenAiDeveloperMessage(
        override val role: String = "developer",
        val name: String? = null,
        val content: String,
    ) : OpenAiMessage()

    @Serializable
    open class OpenAiSystemMessage(
        override val role: String = "system",
        val name: String? = null,
        val content: String,
    ) : OpenAiMessage()
}

@Serializable(with = MessageContentSerializer::class)
sealed class OpenAiMessageContent {
    @Serializable
    data class TextContent(
        val value: String
    ) : OpenAiMessageContent()

    @Serializable
    data class ContentPart(
        val parts: List<OpenAiMessageContentPart>
    ) : OpenAiMessageContent()
}

sealed class OpenAiMessageContentPart {
    TODO
}

@Serializable
data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunctionCall
)

@Serializable
data class OpenAiFunctionCall(
    val name: String,
    /** * 注意：API 返回的 arguments 是一个 JSON 字符串，
     * 而不是解析好的对象，所以这里用 String。
     */
    val arguments: String
)


//自定义序列化器


object MessageContentSerializer : KSerializer<OpenAiMessageContent> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OpenAiMessageContent", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OpenAiMessageContent) {
        when (value) {
            is OpenAiMessageContent.TextContent -> {
                encoder.encodeString(value.value)
            }

            is OpenAiMessageContent.ContentPart -> {
                encoder.encodeSerializableValue(
                    ListSerializer(OpenAiMessageContentPart.serializer()),
                    value.parts
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): OpenAiMessageContent {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JSON")

        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) {
                    OpenAiMessageContent.TextContent(value = element.content)
                } else {
                    throw SerializationException("Expected string, found primitive")
                }
            }

            is JsonArray -> {
                val parts = input.json.decodeFromJsonElement<List<OpenAiMessageContentPart>>(
                    deserializer = ListSerializer(OpenAiMessageContentPart.serializer()),
                    element = element
                )
                OpenAiMessageContent.ContentPart(parts)
            }
        }
    }
}
