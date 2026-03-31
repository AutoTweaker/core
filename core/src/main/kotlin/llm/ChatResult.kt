package io.github.whiteelephant.autotweaker.core.llm

/**
 * 聊天响应的完整结果。
 * 包含模型生成的内容和相关的元数据。
 *
 * @property message 模型生成的消息
 * @property reasoningContent 思维链内容，仅适用于推理模型
 * @property toolCalls 模型调用的工具列表（如果有）
 * @property finishReason 模型停止生成token的原因
 * @property created 创建聊天完成时的Unix时间戳（秒）
 * @property model 生成该completion的模型名
 * @property usage 本次调用的令牌用量统计
 */
data class ChatResult(
    val message: ChatMessage,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val created: Long,
    val model: String,
    val usage: Usage
) {
    /**
     * 提取回复的内容。
     * 这是一个便捷方法。
     */
    fun content(): String? = message.content.chatContent
}