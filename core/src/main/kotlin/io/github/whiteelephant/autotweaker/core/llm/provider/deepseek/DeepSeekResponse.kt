package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class DeepSeekResponse(
    val choices: List<Choice>,
    val usage: DeepSeekUsage,
    override val id: String,
    @Serializable(with = InstantAsLongSerializer::class)
    override val created: Instant,
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
