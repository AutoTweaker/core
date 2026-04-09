package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class MiMoUsage(
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int,

    @SerialName("completion_tokens_details")
    val completionTokensDetails: CompletionTokensDetails? = null,
    @SerialName("prompt_tokens_details")
    val promptTokensDetails: PromptTokensDetails? = null
) {
    @Serializable
    data class CompletionTokensDetails(
        @SerialName("reasoning_tokens")
        val reasoningTokens: Int? = null
    )

    @Serializable
    data class PromptTokensDetails(
        @SerialName("cached_tokens")
        val cachedTokens: Int? = null,
        @SerialName("audio_tokens")
        val audioTokens: Int? = null,
        @SerialName("image_tokens")
        val imageTokens: Int? = null,
        @SerialName("video_tokens")
        val videoTokens: Int? = null,
    )
}
