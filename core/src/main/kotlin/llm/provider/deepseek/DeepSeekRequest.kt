package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class DeepSeekRequest(
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("stream_options")
    val streamOptions: StreamOptions? = null,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice")
    val toolChoice: ToolChoice? = null,
    override val model: String,
    override val thinking: Thinking? = null,
    @SerialName("frequency_penalty")
    override val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    override val presencePenalty: Double? = null,
    @SerialName("response_format")
    override val responseFormat: ResponseFormat? = null,
    override val stop: List<String>? = null,
    override val stream: Boolean? = null,
    override val temperature: Double? = null,
    @SerialName("top_p")
    override val topP: Double? = null

) : OpenAiRequest() {
    @Serializable
    sealed class Message {
        abstract val role: String

        @Serializable
        data class SystemMessage(
            override val role: String = "system",
            val content: String,
            val name: String? = null
        ) : Message()

        @Serializable
        data class UserMessage(
            override val role: String = "user",
            val content: String,
            val name: String? = null
        ) : Message()

        @Serializable
        data class AssistantMessage(
            override val role: String = "assistant",
            val content: String,
            @SerialName("reasoning_content")
            val reasoningContent: String? = null,
            val name: String? = null
        ) : Message()

        @Serializable
        data class ToolMessage(
            override val role: String = "tool",
            val content: String,
            @SerialName("tool_call_id")
            val toolCallId: String
        ) : Message()
    }

    @Serializable
    data class StreamOptions(
        @SerialName("include_usage")
        val includeUsage: Boolean?
    )

    @Serializable
    data class Tool(
        val type: String = "function",
        val function: Function
    ) {
        @Serializable
        data class Function(
            val name: String,
            val description: String?,
            val parameters: Parameters,
            val strict: Boolean? = null
        ) {
            @Serializable
            data class Parameters(
                val type: Property.Type = Property.Type.OBJECT,
                val properties: Map<String, Property> = emptyMap(),
                val required: List<String>? = null
            ) {
                @Serializable
                data class Property(
                    val type: Type,
                    val description: String? = null,
                    val enum: List<String>? = null,
                    val items: Property? = null
                ) {
                    @Serializable
                    enum class Type {
                        @SerialName("string")
                        STRING,

                        @SerialName("number")
                        NUMBER,

                        @SerialName("integer")
                        INTEGER,

                        @SerialName("boolean")
                        BOOLEAN,

                        @SerialName("array")
                        ARRAY,

                        @SerialName("object")
                        OBJECT
                    }
                }
            }
        }
    }


    @Serializable(with = ToolChoice.Serializer::class)
    sealed class ToolChoice {
        data class Simple(val mode: Mode) : ToolChoice() {
            @Serializable
            enum class Mode {
                @SerialName("none")
                NONE,

                @SerialName("auto")
                AUTO,

                @SerialName("required")
                REQUIRED
            }
        }

        @Serializable
        data class Specific(
            val type: String = "function",
            val function: NamedFunction
        ) : ToolChoice() {
            @Serializable
            data class NamedFunction(val name: String)
        }

        companion object {
            val NONE = Simple(Simple.Mode.NONE)
            val AUTO = Simple(Simple.Mode.AUTO)
            val REQUIRED = Simple(Simple.Mode.REQUIRED)

            fun function(name: String) = Specific(function = Specific.NamedFunction(name))
        }

        object Serializer : KSerializer<ToolChoice> {
            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("ToolChoice")

            override fun serialize(encoder: Encoder, value: ToolChoice) {
                val jsonEncoder = encoder as? JsonEncoder
                    ?: throw SerializationException("Only JSON is supported")

                when (value) {
                    is ToolChoice.Simple -> {
                        // 直接序列化内部枚举，输出结果为字符串，例如 "auto"
                        jsonEncoder.encodeJsonElement(
                            jsonEncoder.json.encodeToJsonElement(ToolChoice.Simple.Mode.serializer(), value.mode)
                        )
                    }

                    is ToolChoice.Specific -> {
                        // 序列化整个对象结构
                        jsonEncoder.encodeJsonElement(
                            jsonEncoder.json.encodeToJsonElement(ToolChoice.Specific.serializer(), value)
                        )
                    }
                }
            }

            override fun deserialize(decoder: Decoder): ToolChoice {
                val jsonDecoder = decoder as? JsonDecoder
                    ?: throw SerializationException("Only JSON is supported")
                val element = jsonDecoder.decodeJsonElement()

                return if (element is JsonPrimitive) {
                    // 如果是字符串，解析为枚举并包装在 Simple 中
                    val mode = jsonDecoder.json.decodeFromJsonElement(ToolChoice.Simple.Mode.serializer(), element)
                    ToolChoice.Simple(mode)
                } else {
                    // 如果是对象，解析为 Specific
                    jsonDecoder.json.decodeFromJsonElement(ToolChoice.Specific.serializer(), element)
                }
            }
        }
    }
}
