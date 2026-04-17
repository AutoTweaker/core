package io.github.whiteelephant.autotweaker.core

import io.github.whiteelephant.autotweaker.core.Price
import io.github.whiteelephant.autotweaker.core.agent.llm.AgentChatRequest
import io.github.whiteelephant.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import io.github.whiteelephant.autotweaker.core.agent.llm.agentChat
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.agent.llm.Provider
import io.github.whiteelephant.autotweaker.core.data.json.model.Provider.Model.TokenPrice
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.ZoneId
import java.util.Currency

private val logger = LoggerFactory.getLogger("io.github.whiteelephant.autotweaker.core.MainKt")

fun main() {
    logger.info("AutoTweaker 启动中...")
    val apiKey = System.getenv("MIMO_API_KEY")
        ?: throw IllegalStateException("Please set your MiMo API key as an environment variable.")

    val model = Model(
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
    )

    val wrongModel = Model(
        name = "mimo-v2-flash",
        provider = Provider(
            name = "mimo",
            baseUrl = Url("https://api.xiaomimimo.com/v1"),
            apiKey = "1111111",
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
    )

    val toolCallTimestamp = Instant.parse("2026-04-11T09:03:33Z")

    val request = AgentChatRequest(
        model = wrongModel,
        fallbackModels = listOf(model),
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
            systemPrompt = "你是MiMo（中文名称也是MiMo），是小米公司研发的AI智能助手。\n今天的日期：{date} {week}，你的知识截止日期是2024年12月。",
            compactedRounds = null,
            summarizedMessages = null,
            historyRounds = null,
            currentRound = AgentContext.CurrentRound(
                userMessage = AgentContext.Message.User(
                    content = "北京现在的天气怎么样？",
                    timestamp = Clock.System.now(),
                ),
                turns = listOf(
                    AgentContext.Turn(
                        assistantMessage = AgentContext.Message.Assistant(
                            reasoning = "用户询问北京现在的天气情况。我需要调用天气查询工具来获取信息。根据工具定义，我需要提供城市名称，温度单位是可选的。用户没有指定温度单位，我可以使用默认值，或者不提供该参数。为了更准确，我应该询问用户偏好，但考虑到用户只是简单询问，我可以先使用默认单位（可能是摄氏度）。不过，工具描述中没有明确说明默认单位，所以我最好还是提供一个明确的单位。考虑到用户在北京，中国通常使用摄氏度，所以我应该使用celsius。现在，我需要生成一个函数调用。",
                            content = null,
                            model = model,
                            timestamp = toolCallTimestamp,
                        ),
                        tools = listOf(
                            AgentContext.Message.Tool(
                                name = "get_weather",
                                callId = "call_e3de44dd68d741508d5dc896",
                                call = AgentContext.Message.Tool.Call(
                                    arguments = """{"city": "北京", "unit": "celsius"}""",
                                    timestamp = toolCallTimestamp,
                                    model = model,
                                ),
                                result = AgentContext.Message.Tool.Result(
                                    content = """{"city": "北京", "temperature": 22, "unit": "celsius", "condition": "晴", "humidity": 35, "wind": "南风 3级"}""",
                                    timestamp = Clock.System.now(),
                                    status = AgentContext.Message.Tool.Result.Status.SUCCESS
                                ),
                            )
                        ),
                    )
                ),
                assistantMessage = null,
                pendingToolCalls = null,
            ),
        ),
    )

    runBlocking {
        agentChat(request).collect { result ->
            when (result) {
                is AgentChatStreamResult.Reasoning -> println("[Reasoning] ${result.reasoningContent}\n==========\n\n")
                is AgentChatStreamResult.Outputting -> println(result.content)
                is AgentChatStreamResult.Finished -> println("\n\n[Finished] ${result.result}")
                is AgentChatStreamResult.Failing -> println("[Error] ${result.errors}")
            }
        }
    }
    logger.info("AutoTweaker 执行完成")
}
