package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.serialization.json.JsonElement

/**
 * 聊天请求的配置。
 * 包含所有可配置的参数，用于控制模型的行为。
 *
 * @property model 使用的模型标识符，例如"deepseek-chat"或"gpt-4"
 * @property messages 消息列表，包含用户输入和可能的系统提示
 * @property thinking 思考模式控制，仅适用于支持推理的模型
 * @property temperature 温度参数，控制输出的随机性（0-2），值越高越随机
 * @property maxTokens 最大生成令牌数，限制模型输出的长度
 * @property tools 工具调用定义，允许模型调用外部函数
 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val thinking: Boolean? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val tools: List<Tool>? = null,
    val stream: Boolean = true
)
