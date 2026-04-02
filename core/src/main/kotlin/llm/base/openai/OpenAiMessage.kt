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

import kotlinx.serialization.serializer

sealed class OpenAiMessage {
    abstract val role: String

    @Serializable
    open class OpenAiDeveloperMessage(
        override val role: String = "developer",
        val name: String? = null,
        val content: OpenAiMessageContent,
    ) : OpenAiMessage()

    @Serializable
    open class OpenAiSystemMessage(
        override val role: String = "system",
        val name: String? = null,
        val content: OpenAiMessageContent,
    ) : OpenAiMessage()

    @Serializable
    open class OpenAiUserMessage(
        override val role: String = "user",
        val name: String? = null,
        val content: OpenAiMessageContent,
    ) : OpenAiMessage()

    //TODO 待完善
    @Serializable
    open class OpenAiAssistantMessage(
        override val role: String = "assistant",
        val name: String? = null,
        val content: OpenAiMessageContent,
    ) : OpenAiMessage()
}

//string或list二选一，用于OpenAiMessage
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

//多模态使用，可包含多种格式
@Serializable
sealed class OpenAiMessageContentPart {

    @Serializable
    @SerialName("type")
    data class TextPart(
        val text: String
    ) : OpenAiMessageContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImagePart(
        @SerialName("image_url") val imageUrl: ImageUrl
    ) : OpenAiMessageContentPart() {
        @Serializable
        data class ImageUrl(
            val url: String,
            val detail: Detail? = null
        ) {
            @Serializable
            enum class Detail {
                @SerialName("auto")
                AUTO,

                @SerialName("low")
                LOW,

                @SerialName("high")
                HIGH
            }
        }
    }

    @Serializable
    @SerialName("input_audio")
    data class InputAudioPart(
        @SerialName("input_audio") val inputAudio: InputAudio
    ) : OpenAiMessageContentPart() {
        @Serializable
        data class InputAudio(
            val data: String,
            val format: Format
        ) {
            @Serializable
            enum class Format {
                @SerialName("waw")
                WAW,

                @SerialName("mp3")
                MP3
            }
        }
    }

    @Serializable
    @SerialName("file")
    data class File(
        val file: File
    ) {
        @Serializable
        data class File(
            @SerialName("file_data") val fileData: String? = null,
            @SerialName("file_id") val fileId: String? = null,
            @SerialName("filename") val fileName: String? = null
        )
    }
}


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
