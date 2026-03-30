package io.github.whiteelephant.autotweaker.api.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DeepSeek聊天补全API的请求体。
 * 参考：https://api-docs.deepseek.com/zh-cn/api/create-chat-completion
 *
 * DeepSeek API与OpenAI API基本兼容，但有一些额外的字段。
 *
 * @property model 使用的模型标识符，例如"deepseek-chat"或"deepseek-reasoner"
 * @property messages 消息列表，包含角色和内容
 * @property thinking 思考模式控制，可选字段
 * @property temperature 温度参数，控制输出的随机性（0-2），默认0.7
 * @property maxTokens 最大生成令牌数
 * @property topP 核心采样参数（0-1）
 * @property frequencyPenalty 频率惩罚（-2到2）
 * @property presencePenalty 存在惩罚（-2到2）
 * @property stream 是否使用流式输出，默认为false
 * @property tools 工具调用定义，可选
 * @property responseFormat 响应格式，如JSON模式
 */
@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val thinking: Boolean? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stream: Boolean = false,
    val tools: List<DeepSeekTool>? = null,
    val responseFormat: DeepSeekResponseFormat? = null
)