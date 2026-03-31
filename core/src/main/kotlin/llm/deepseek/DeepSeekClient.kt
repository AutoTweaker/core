package io.github.whiteelephant.autotweaker.core.llm.deepseek

import io.github.whiteelephant.autotweaker.core.llm.*
import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import io.ktor.client.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

class DeepSeekClient(
    apiKey: String,
    httpClient: HttpClient,
    baseUrl: String = "https://api.deepseek.com"
) : AbstractOpenAiClient(apiKey, baseUrl, httpClient) {

    // --- 1. 适配 DeepSeek 特有的请求结构 ---

    @Serializable
    private data class DeepSeekRequest(
        val model: String,
        val messages: List<DeepSeekMessage>,
        val thinking: ThinkingConfig? = null,
        val temperature: Double? = null,
        val stream: Boolean = false,
        val tools: List<Tool>? = null
    )

    @Serializable
    private data class DeepSeekMessage(
        val role: String,
        val content: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
        @SerialName("tool_call_id") val toolCallId: String? = null
    )

    @Serializable
    private data class ThinkingConfig(val type: String)

    override fun createRequestBody(request: ChatRequest): Any {
        // 1. 将业务层消息映射为 DeepSeek 协议层消息
        // 这一步至关重要：它确保了 Assistant 消息中的 reasoning_content 和工具调用能被正确回传
        val mappedMessages = request.messages.map { msg ->
            when (msg) {
                is ChatMessage.SystemMessage -> DeepSeekMessage(
                    role = "system",
                    content = msg.content
                )

                is ChatMessage.UserMessage -> DeepSeekMessage(
                    role = "user",
                    content = msg.content
                )

                is ChatMessage.AssistantMessage -> DeepSeekMessage(
                    role = "assistant",
                    content = msg.content,
                    reasoningContent = msg.reasoningContent // 重要：回传上一轮的思考过程
                )

                is ChatMessage.ToolMessage -> DeepSeekMessage(
                    role = "tool",
                    content = msg.content,
                    toolCallId = msg.toolId // 重要：工具结果必须关联 tool_call_id
                )
                // 默认处理
                else -> DeepSeekMessage(
                    role = "user",
                    content = msg.content
                )
            }
        }

        // 2. 检查是否开启了思考模式
        val isThinkingEnabled = request.thinking == true

        // 3. 构建请求体
        return DeepSeekRequest(
            model = request.model,
            messages = mappedMessages,
            stream = request.stream,
            tools = request.tools,
            // 根据文档：开启思考模式时，必须设置 thinking 对象
            thinking = if (isThinkingEnabled) ThinkingConfig("enabled") else null,
            // 根据文档：开启思考模式时，不支持 temperature 参数，必须设为 null
            temperature = if (isThinkingEnabled) null else request.temperature
        )
    }

    // --- 2. 映射全量响应 (支持思维链 & 工具调用) ---

    override fun mapToChatResult(response: OpenAiResponse): ChatResult {
        val choice = response.choices.firstOrNull()
        val msg = choice?.message

        return ChatResult(
            message = ChatMessage.AssistantMessage(
                content = msg?.content ?: "",
                // 这里的 reasoningContent 需要你在 OpenAiMessage 类里预先定义好
                reasoningContent = msg?.reasoningContent,
                createdAt = response.created ?: (System.currentTimeMillis() / 1000),
                model = response.model ?: "deepseek-chat"
            ),
            // 映射工具调用
            toolCalls = msg?.toolCalls?.map {
                ToolCall(it.id, it.function.name, it.function.arguments)
            },
            usage = response.usage?.let { u ->
                Usage(u.totalTokens, 0.0, 0.0, 0.0, 0.0)
            },
            finishReason = choice?.finishReason
        )
    }

    // --- 3. 映射流式切片 (支持思维链增量) ---

    override fun mapChunkToChatResult(chunk: OpenAiStreamChunk): ChatResult {
        val choice = chunk.choices.firstOrNull()
        val delta = choice?.delta

        return ChatResult(
            message = ChatMessage.AssistantMessage(
                content = delta?.content ?: "",
                // 映射流式返回的思维链内容
                reasoningContent = delta?.reasoningContent,
                createdAt = System.currentTimeMillis() / 1000,
                model = chunk.model ?: "deepseek-chat"
            ),
            // 映射流式工具调用碎片
            toolCalls = delta?.toolCalls?.map {
                ToolCall(it.id ?: "", it.function?.name ?: "", it.function?.arguments ?: "")
            },
            finishReason = choice?.finishReason
        )
    }
}
