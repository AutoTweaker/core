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
     * @return 返回ChatResult的流，每个chunk包含部分生成的内容
     */
    suspend fun chatStream(request: ChatRequest): Flow<ChatResult>
}
