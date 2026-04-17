package io.github.autotweaker.core.llm.provider.deepseek

import io.github.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DeepSeekStreamChunk(
    val choices: List<Choice>,
    val usage: DeepSeekUsage? = null,
    override val id: String,
    @Serializable(with = InstantAsLongSerializer::class)
    override val created: Instant,
    override val model: String,
) : OpenAiStreamChunk() {

    @Serializable
    data class Choice(
        val index: Int,
        val delta: Delta,
        @SerialName("finish_reason")
        val finishReason: DeepSeekFinishReason? = null
    ) {
        @Serializable
        data class Delta(
            val role: String? = null,
            val content: String? = null,
            @SerialName("reasoning_content")
            val reasoningContent: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<ToolCall>? = null
        )

        @Serializable
        data class ToolCall(
            val index: Int,
            val id: String? = null,
            val type: String? = null,
            val function: Function? = null
        ) {
            @Serializable
            data class Function(
                val name: String? = null,
                val arguments: String? = null
            )
        }
    }
}
