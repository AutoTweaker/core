package io.github.whiteelephant.autotweaker.core.llm

/**
 * 大模型（LLM）客户端的接口。
 * 这个接口定义了调用大模型API的基本操作，允许不同的实现（如DeepSeek、OpenAI等）。
 * 通过接口，我们可以轻松切换不同的提供商，符合"可扩展"的设计原则。
 */
interface LlmClient {
    /**
     * 发送聊天消息并获取回复。
     *
     * @param messages 消息列表，包含用户输入和可能的系统提示。
     * @param model 使用的模型标识符（可选，有些客户端可能有默认模型）。
     * @return 模型返回的文本回复。
     */
    suspend fun chat(messages: List<ChatMessage>, model: String? = null): String
}