package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

abstract class OpenAiResponse<Message : OpenAiMessage> {
    abstract val id: String?
    abstract val choices: List<OpenAiChoice<Message>>
    abstract val usage: OpenAiUsage?
    abstract val created: Long?
    abstract val model: String?
}

@Serializable
data class OpenAiChoice<Message : OpenAiMessage>(
    val index: Int,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
