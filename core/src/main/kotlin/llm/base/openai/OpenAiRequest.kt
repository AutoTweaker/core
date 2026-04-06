package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

import io.github.whiteelephant.autotweaker.core.llm.Tool

abstract class OpenAiRequest {
    abstract val model: String?
    abstract val thinking: Thinking?
    abstract val frequencyPenalty: Double?
    abstract val presencePenalty: Double?
    abstract val responseFormat: ResponseFormat?
    abstract val stop: List<String>?
    abstract val stream: Boolean?
    abstract val temperature: Double?
    abstract val topP: Double?

    @Serializable
    data class Thinking(
        val type: Type
    ) {
        @Serializable
        enum class Type {
            @SerialName("enabled")
            ENABLED,

            @SerialName("disabled")
            DISABLED
        }
    }

    @Serializable
    data class ResponseFormat(
        val type: Type
    ) {
        @Serializable
        enum class Type {
            @SerialName("text")
            TEXT,

            @SerialName("json_object")
            JSON_OBJECT
        }
    }
}
