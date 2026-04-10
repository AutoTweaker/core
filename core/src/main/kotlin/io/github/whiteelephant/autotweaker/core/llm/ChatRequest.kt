package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val thinking: Boolean? = null,
    val stream: Boolean = false,

    val maxTokens: Int? = null,
    val tools: List<Tool>? = null,
    val toolCallRequired: Boolean? = null,

    val temperature: Double? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
) {
    data class Tool(
        val name: String,
        val description: String,
        val parameters: Parameters
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
