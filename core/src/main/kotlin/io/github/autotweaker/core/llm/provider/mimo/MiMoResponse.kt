package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MiMoResponse(
    val choices: List<Choice>,
    val usage: MiMoUsage,
    override val id: String,
    @Serializable(with = InstantAsLongSerializer::class)
    override val created: Instant,
    override val model: String
) : OpenAiResponse() {
    @Serializable
    data class Choice(
        val index: Int,
        val message: Message,
        @SerialName("finish_reason")
        val finishReason: MiMoFinishReason,
    ) {
        @Serializable
        data class Message(
            val content: String?,
            @SerialName("reasoning_content")
            val reasoningContent: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<MiMoToolCall>? = null
        )
    }
}
