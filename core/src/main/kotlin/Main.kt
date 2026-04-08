package io.github.whiteelephant.autotweaker.core

import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.provider.deepseek.DeepSeekClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main() {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
    }
    val client = DeepSeekClient(
        apiKey = System.getenv("DEEPSEEK_API_KEY") ?: throw IllegalStateException("Please set your DeepSeek API key as an environment variable."),
        httpClient = httpClient,
    )
    runBlocking {
        client.chat(
            ChatRequest(
                model = "deepseek-chat",
                messages = listOf(
                    ChatMessage.UserMessage(
                        content = "你好！",
                        createdAt = System.currentTimeMillis(),
                    )
                ),
                stream = true,
                thinking = true
            )
        ).collect { result ->
            println(result)
        }
    }
}
