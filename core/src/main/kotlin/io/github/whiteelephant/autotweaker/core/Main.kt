package io.github.whiteelephant.autotweaker.core

import io.github.whiteelephant.autotweaker.core.Price
import io.github.whiteelephant.autotweaker.core.agent.AgentChatRequest
import io.github.whiteelephant.autotweaker.core.agent.AgentChatStreamResult
import io.github.whiteelephant.autotweaker.core.agent.AgentContext
import io.github.whiteelephant.autotweaker.core.agent.chatStream
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.agent.llm.Provider
import io.github.whiteelephant.autotweaker.core.agent.llm.TokenPrice
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

fun main() {
    val apiKey = System.getenv("MIMO_API_KEY")
        ?: throw IllegalStateException("Please set your MiMo API key as an environment variable.")

    val request = AgentChatRequest(
        model = Model(
            name = "mimo-v2-flash",
            provider = Provider(
                name = "mimo",
                baseUrl = Url("https://api.xiaomimimo.com/v1"),
                apiKey = apiKey,
                errorHandlingRules = emptyList(),
            ),
            contextWindow = 256_000,
            maxOutputTokens = 64_000,
            price = TokenPrice(
                inputPrice = listOf(
                    TokenPrice.PriceTier(
                        fromTokens = 0,
                        price = Price(BigDecimal("0.70"), Currency.getInstance("CNY")),
                        cachedPrice = Price(BigDecimal("0.07"), Currency.getInstance("CNY")),
                    ),
                ),
                outputPrice = listOf(
                    TokenPrice.PriceTier(
                        fromTokens = 0,
                        price = Price(BigDecimal("2.10"), Currency.getInstance("CNY")),
                    ),
                ),
            ),
            supportsStreaming = true,
            supportsToolCalls = true,
            supportsReasoning = true,
            supportsImage = false,
        ),
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
        context = AgentContext(
            systemPrompt = null,
            compactedRounds = emptyList(),
            summarizedMessages = null,
            historyRounds = emptyList(),
            currentRound = AgentContext.CurrentRound(
                userMessage = AgentContext.Message.User(
                    content = "北京现在的天气怎么样？",
                    timestamp = Instant.now(),
                ),
                turns = emptyList(),
            ),
        ),
    )

    runBlocking {
        chatStream(request).collect { result ->
            when (result) {
                is AgentChatStreamResult.Reasoning -> println("[Reasoning] ${result.reasoningContent}")
                is AgentChatStreamResult.Outputting -> println(result.content)
                is AgentChatStreamResult.Finished -> println("\n[Finished] ${result.result}")
            }
        }
    }
}
