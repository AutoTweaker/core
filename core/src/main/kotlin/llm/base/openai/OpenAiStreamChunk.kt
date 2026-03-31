package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
abstract class OpenAiStreamChunk {
    abstract val id: String?
    abstract val choices: List<OpenAiChunkChoice>
    abstract val model: String?
}

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
    // 添加这一行
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiStreamToolCall>? = null
)
