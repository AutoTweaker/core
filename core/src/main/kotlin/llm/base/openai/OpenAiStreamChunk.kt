package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OpenAiStreamChunk(
    val id: String? = null,
    val choices: List<OpenAiChunkChoice>,
    val model: String? = null
)

@Serializable
data class OpenAiChunkChoice(
    val index: Int,
    val delta: OpenAiDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiStreamToolCall>? = null
)
