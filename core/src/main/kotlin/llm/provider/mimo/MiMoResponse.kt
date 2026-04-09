package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MiMoResponse(
    val choices: List<Choice>,
    val usage: MiMoUsage,
    override val id: String,
    override val created: Long,
    override val model: String
) : OpenAiResponse() {
    @Serializable
    data class Choice(
        val index: Int,
        val message: MiMoMessage.AssistantMessage,
        @SerialName("finish_reason")
        val finishReason: String,
    )
}
