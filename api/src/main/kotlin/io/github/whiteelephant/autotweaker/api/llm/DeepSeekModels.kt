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

/**
 * DeepSeek工具定义。
 * 用于工具调用的功能描述。
 */
@Serializable
data class DeepSeekTool(
    val type: String = "function",
    val function: DeepSeekFunction
)

/**
 * 工具函数定义。
 * 描述函数名称、描述和参数。
 */
@Serializable
data class DeepSeekFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null
)

/**
 * 响应格式定义，如JSON模式。
 * 通过responseFormat参数，可以要求模型返回特定格式的响应。
 */
@Serializable
data class DeepSeekResponseFormat(
    val type: String = "json_object"
)

/**
 * DeepSeek聊天补全API的响应体。
 * API返回的JSON会被反序列化为这个类的实例。
 */
@Serializable
data class DeepSeekChatResponse(
    val id: String,
    val choices: List<DeepSeekChoice>,
    val created: Long,
    val model: String,
    val usage: DeepSeekUsage,
    val systemFingerprint: String? = null
) {
    /**
     * 提取第一个选择的内容。
     * 这是一个便捷方法，通常我们只关心第一个回复。
     */
    fun firstChoiceContent(): String? = choices.firstOrNull()?.message?.content
}

/**
 * 表示模型停止生成的原因。
 * 参考：https://api-docs.deepseek.com/zh-cn/api/create-chat-completion
 */
enum class FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    INSUFFICIENT_SYSTEM_RESOURCE
}

/**
 * 表示一个回复选项。
 * 包含消息内容、索引和停止原因。
 */
@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: ChatMessage,
    val finishReason: String? = null
)

/**
 * 表示令牌使用情况。
 * 统计本次API调用消耗的令牌数量。
 */
@Serializable
data class DeepSeekUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)