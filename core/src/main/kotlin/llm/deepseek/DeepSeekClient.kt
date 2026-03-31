package io.github.whiteelephant.autotweaker.core.llm.deepseek

import io.github.whiteelephant.autotweaker.core.llm.LlmClient
import io.github.whiteelephant.autotweaker.core.llm.ChatMessage

/**
 * DeepSeek API客户端实现。
 *
 * 这个类实现了LlmClient接口，提供了与DeepSeek API的交互能力。
 * 它负责处理HTTP请求、序列化、错误处理等底层细节。
 *
 * 设计原则：
 * 1. 可扩展：通过实现LlmClient接口，可以轻松替换为其他提供商
 * 2. 可配置：API密钥、基础URL等通过构造函数注入
 * 3. 错误处理：使用Result类型包装可能失败的调用
 */
class DeepSeekClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com"
) : LlmClient {

    override suspend fun chat(messages: List<ChatMessage>, model: String?): String {
        TODO("Not yet implemented")
    }
}