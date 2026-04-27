package io.github.autotweaker.core

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.llm.*
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.LlmClientLoader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = LoggerFactory.getLogger(object {}::class.java.enclosingClass)

fun main() {
	Settings.init()
	
	val apiKey = System.getenv("MIMO_API_KEY")
		?: throw IllegalStateException("Please set your MiMo API key as an environment variable.")
	
	val mimoProviderInfo = LlmClientLoader.load("mimo").providerInfo
	val model = Model(
		name = mimoProviderInfo.models.first().id,
		provider = Provider(
			name = mimoProviderInfo.name,
			baseUrl = mimoProviderInfo.baseUrl,
			apiKey = apiKey,
			errorHandlingRules = mimoProviderInfo.errorHandlingRules,
		),
		modelInfo = mimoProviderInfo.models.first(),
		config = null
	)
	val wrongModel = Model(
		name = "??",
		provider = model.provider,
		modelInfo = model.modelInfo,
		config = null
	)
	
	val toolCallTimestamp = Instant.parse("2026-04-11T09:03:33Z")
	
	@Suppress("unused", "UnusedVariable") val turn = listOf(
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
						reason = "获取北京的天气情况"
					),
					result = AgentContext.Message.Tool.Result(
						content = """{"city": "北京", "temperature": 22, "unit": "celsius", "condition": "晴", "humidity": 35, "wind": "南风 3级"}""",
						timestamp = Clock.System.now(),
						status = AgentContext.Message.Tool.Result.Status.SUCCESS
					),
				)
			),
		)
	)
	
	val request = AgentChatRequest(
		model = wrongModel,
		fallbackModels = listOf(model),
		thinking = true,
		tools = listOf(
			ChatRequest.Tool(
				name = "get_weather",
				description = "获取指定城市的当前天气信息",
				parameters = buildJsonObject {
					put("type", "object")
					putJsonObject("properties") {
						put("city", buildJsonObject {
							put("type", "string")
							put("description", "城市名称，例如：北京、上海")
						})
						put("unit", buildJsonObject {
							put("type", "string")
							put("description", "温度单位")
							putJsonArray("enum") {
								add("celsius")
								add("fahrenheit")
							}
						})
					}
					putJsonArray("required") {
						add("city")
					}
				}
			)
		),
		context = AgentContext(
			systemPrompt = "你是MiMo（中文名称也是MiMo），是小米公司研发的AI智能助手。\n今天的日期：{date} {week}，你的知识截止日期是2024年12月。",
			compactedRounds = null,
			historyRounds = null,
			currentRound = AgentContext.CurrentRound(
				userMessage = AgentContext.Message.User(
					summarizedMessage = null,
					content = "北京现在的天气怎么样？",
					timestamp = Clock.System.now(),
				),
				assistantMessage = null,
				pendingToolCalls = null,
				turns = null
			),
		),
	)
	
	runBlocking {
		agentChat(request).collect { result ->
			when (result) {
				is AgentChatStreamResult.Reasoning -> logger.info("[Reasoning] {}", result.reasoningContent)
				is AgentChatStreamResult.Outputting -> logger.info("[Outputting] {}", result.content)
				is AgentChatStreamResult.Finished -> logger.info("[Finished] {}", result.result)
				is AgentChatStreamResult.Failing -> logger.error("[Failing] {}", result.errors)
			}
		}
	}
}
