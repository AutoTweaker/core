package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DeepSeekResponse(
    val choices: List<Choice>,
    val usage: DeepSeekUsage,
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
}
