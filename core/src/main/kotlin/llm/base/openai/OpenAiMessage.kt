package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String? = null,
    // 模型请求调用工具时，会返回这个列表
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    // 当你作为 "tool" 角色回复结果时，需要关联对应的 id
    @SerialName("tool_call_id") val toolCallId: String? = null
)

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
