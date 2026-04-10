package io.github.whiteelephant.autotweaker.core

import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.LlmClientLoader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Instant

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
    val client = LlmClientLoader.load(
        name = "mimo",
        apiKey = System.getenv("MIMO_API_KEY") ?: throw IllegalStateException("Please set your MiMo API key as an environment variable."),
        httpClient = httpClient,
    )
    runBlocking {
        client.chat(
            ChatRequest(
                model = "mimo-v2-flash",
                messages = listOf(
                    ChatMessage.UserMessage(
                        content = "北京现在的天气怎么样？",
                        createdAt = Instant.now(),
                    ),
                    ChatMessage.AssistantMessage(
                        content = null,
                        createdAt = Instant.now(),
                        model = "deepseek-chat",
                        toolCalls = listOf(
                            ChatMessage.AssistantMessage.ToolCall(
                                id = "call_00_yMw2dEIygMrG8UXM9NQ7A44u",
                                name = "get_weather",
                                arguments = """{"city": "北京", "unit": "celsius"}"""
                            )
                        )
                    ),
                    ChatMessage.ToolMessage(
                        content = """{"temperature": 19, "unit": "celsius", "condition": "晴", "humidity": 45}""",
                        createdAt = Instant.now(),
                        toolCallId = "call_00_yMw2dEIygMrG8UXM9NQ7A44u"
                    )
                ),
                stream = true,
                thinking = true,
                tools = listOf(
                    ChatRequest.Tool(
                        name = "get_weather",
                        description = "获取指定城市的当前天气信息",
                        parameters = ChatRequest.Tool.Parameters(
                            properties = mapOf(
                                "city" to ChatRequest.Tool.Parameters.Property(
                                    type = ChatRequest.Tool.Parameters.Property.Type.STRING,
                                    description = "城市名称，例如：北京、上海"
                                ),
                                "unit" to ChatRequest.Tool.Parameters.Property(
                                    type = ChatRequest.Tool.Parameters.Property.Type.STRING,
                                    description = "温度单位",
                                    enum = listOf("celsius", "fahrenheit")
                                )
                            ),
                            required = listOf("city")
                        )
                    )
                ),
            )
        ).collect { result ->
            println(result)
        }
    }
}
