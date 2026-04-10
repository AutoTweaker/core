package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MiMoStreamChunk(
    val choices: List<Choice>,
    val usage: MiMoUsage? = null,
    override val id: String,
    override val created: Long,
    override val model: String,
) : OpenAiStreamChunk() {
    @Serializable
    data class Choice(
        val index: Int,
        val delta: Delta,
        @SerialName("finish_reason")
        val finishReason: String? = null
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
