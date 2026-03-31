package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * 大模型（LLM）客户端的接口。
 * 这个接口定义了调用大模型API的基本操作，允许不同的实现（如DeepSeek、OpenAI等）。
 * 通过接口，我们可以轻松切换不同的提供商，符合"可扩展"的设计原则。
 */
interface LlmClient {
    /**
     * 发送聊天请求并获取完整的响应。
     *
     * @param request 聊天请求配置
     * @return 包含完整响应信息的ChatResult
     */
    suspend fun chat(request: ChatRequest): ChatResult

    /**
     * 发送聊天请求并获取流式响应。
     * 当需要实时接收模型输出时使用此方法。
     *
     * @param request 聊天请求配置
     * @return 返回ChatChunk的流，每个chunk包含部分生成的内容
     */
    suspend fun chatStream(request: ChatRequest): Flow<ChatChunk>
}

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
    val tools: List<Tool>? = null
)

/**
 * 工具定义。
 * 用于工具调用的功能描述。
 *
 * @property name 函数名称
 * @property description 函数描述
 * @property parameters 函数参数的JSON Schema
 */
data class Tool(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement
)


/**
 * 表示令牌使用情况。
 * 统计本次API调用消耗的令牌数量。
 *
 * @property totalTokens 总token数量
 * @property contextWindowUsage 上下文窗口占用量（百分比）
 * @property inputCost 输入花费（单位：元）
 * @property outputCost 输出花费（单位：元）
 * @property totalCost 总花费（单位：元）
 */
data class Usage(
    val totalTokens: Int,
    val contextWindowUsage: Double,
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double
)

/**
 * 工具调用。
 * 表示模型决定调用一个工具。
 *
 * @property name 要调用的工具名称
 * @property arguments 调用参数（JSON字符串）
 */
data class ToolCall(
    val name: String,
    val arguments: String
)

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
    fun content(): String? = message.content
}
