package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.LlmClientLoader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

private val defaultHttpClient by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
    }
}

/**
 * 动态调用 LLM。
 *
 * @param provider provider 名称（SPI 注册的 [LlmClientFactory][io.github.whiteelephant.autotweaker.core.llm.LlmClientFactory] name）
 * @param apiKey API 密钥
 * @param baseUrl 自定义 base URL（可选）
 * @param request 动态构造的 [ChatRequest]
 */
suspend fun chat(
    provider: String,
    apiKey: String,
    baseUrl: Url? = null,
    request: ChatRequest,
): Flow<ChatResult> {
    val client = LlmClientLoader.load(
        name = provider,
        apiKey = apiKey,
        httpClient = defaultHttpClient,
        baseUrl = baseUrl,
    )
    return client.chat(request)
}
