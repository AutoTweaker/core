package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DeepSeekResponse(
    val choices: List<Choice>,
    val usage: Usage,
    override val id: String,
    override val created: Long,
    override val model: String
) : OpenAiResponse() {

    @Serializable
    data class Choice(
        val index: Int,
        val message: DeepSeekMessage.AssistantMessage,
        @SerialName("finish_reason")
        val finishReason: String,
    )

    @Serializable
    data class Usage(
        //基本统计
        @SerialName("completion_tokens")
        val completionTokens: Int,
        @SerialName("prompt_tokens")
        val promptTokens: Int,
        @SerialName("total_tokens")
        val totalTokens: Int,

        //缓存统计
        @SerialName("prompt_cache_hit_tokens")
        val promptCacheHitTokens: Int? = null,
        @SerialName("prompt_cache_miss_tokens")
        val promptCacheMissTokens: Int? = null,

        //详细统计
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
            val cachedTokens: Int? = null
        )
    }
}
