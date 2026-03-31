package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OpenAiResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage? = null,
    val created: Long? = null,
    val model: String? = null
)

@Serializable
data class OpenAiChoice(
    val index: Int,
    val message: OpenAiMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
