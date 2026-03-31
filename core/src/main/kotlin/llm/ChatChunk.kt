package io.github.whiteelephant.autotweaker.core.llm

/**
 * 流式输出的一个数据块（chunk）。
 * 当使用流式输出时，API返回一系列这样的数据块。
 *
 * @property content 本次chunk生成的内容
 * @property reasoningContent 仅适用于推理模型，内容为assistant消息中在最终答案之前的推理内容
 * @property toolCalls 完整的工具调用列表（当模型决定调用工具时返回）
 * @property finishReason 模型停止生成token的原因（只在最后一个chunk中返回）
 * @property usage 本次调用的令牌用量统计（只在最后一个chunk中返回）
 */
data class ChatChunk(
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val usage: Usage? = null
)