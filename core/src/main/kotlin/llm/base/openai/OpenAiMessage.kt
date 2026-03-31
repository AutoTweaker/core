package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

abstract class OpenAiMessage {
    abstract val role: String
    abstract val content: String?
    abstract val reasoningContent: String?
    abstract val toolCalls: List<OpenAiToolCall>?
    abstract val toolCallId: String?
}

@Serializable
data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunctionCall
)

@Serializable
data class OpenAiFunctionCall(
    val name: String,
    /** * 注意：API 返回的 arguments 是一个 JSON 字符串，
     * 而不是解析好的对象，所以这里用 String。
     */
    val arguments: String
)
