package io.github.autotweaker.core.llm.provider.mimo

import com.google.auto.service.AutoService
import io.github.autotweaker.core.Price
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.llm.*
import io.github.autotweaker.core.llm.base.openai.AbstractOpenAiClient
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import io.ktor.util.reflect.*
import kotlinx.serialization.serializer
import java.math.BigDecimal
import java.util.*

@AutoService(LlmClient::class)
class MiMoClient : AbstractOpenAiClient<MiMoRequest, MiMoResponse, MiMoStreamChunk>(
	requestTypeInfo = typeInfo<MiMoRequest>(),
	responseTypeInfo = typeInfo<MiMoResponse>(),
	chunkSerializer = serializer<MiMoStreamChunk>(),
) {
	private val mimoProPrice = SettingItem.Value.Providers.Provider.Model.TokenPrice(
		inputPrice = listOf(
			SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
				fromTokens = 0,
				toTokens = 256_000,
				price = Price(
					amount = BigDecimal(7),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
				cachedPrice = Price(
					amount = BigDecimal(1.4),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
			),
			SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
				fromTokens = 256_000,
				price = Price(
					amount = BigDecimal(14),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
				cachedPrice = Price(
					amount = BigDecimal(2.8),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
			)
		),
		outputPrice = listOf(
			SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
				fromTokens = 0,
				toTokens = 256_000,
				price = Price(
					amount = BigDecimal(21),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
			),
			SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
				fromTokens = 256_000,
				price = Price(
					amount = BigDecimal(42),
					currency = Currency.getInstance(Locale.CHINA),
					unit = 100_0000
				),
			)
		),
	)
	
	override val providerInfo: LlmClient.ProviderInfo = LlmClient.ProviderInfo(
		name = "mimo",
		baseUrl = Url("https://api.xiaomimimo.com/v1"),
		models = listOf(
			SettingItem.Value.Providers.Provider.Model.ModelInfo(
				id = "mimo-v2-pro",
				contextWindow = 100_0000,
				maxOutputTokens = 128_000,
				price = mimoProPrice,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = false,
				supportsJsonOutput = true
			),
			SettingItem.Value.Providers.Provider.Model.ModelInfo(
				id = "mimo-v2.5-pro",
				contextWindow = 100_0000,
				maxOutputTokens = 128_000,
				price = mimoProPrice,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = false,
				supportsJsonOutput = true
			),
			SettingItem.Value.Providers.Provider.Model.ModelInfo(
				id = "mimo-v2-omni",
				contextWindow = 256_000,
				maxOutputTokens = 128_000,
				price = SettingItem.Value.Providers.Provider.Model.TokenPrice(
					inputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							price = Price(
								amount = BigDecimal(2.8),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
							cachedPrice = Price(
								amount = BigDecimal(0.56),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
					),
					outputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							price = Price(
								amount = BigDecimal(14),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
					),
				),
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = true,
				supportsJsonOutput = true
			),
			SettingItem.Value.Providers.Provider.Model.ModelInfo(
				id = "mimo-v2.5",
				contextWindow = 100_0000,
				maxOutputTokens = 128_000,
				price = SettingItem.Value.Providers.Provider.Model.TokenPrice(
					inputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							toTokens = 256_000,
							price = Price(
								amount = BigDecimal(2.8),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
							cachedPrice = Price(
								amount = BigDecimal(0.56),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 256_000,
							price = Price(
								amount = BigDecimal(5.6),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
							cachedPrice = Price(
								amount = BigDecimal(1.12),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						)
					),
					outputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							toTokens = 256_000,
							price = Price(
								amount = BigDecimal(14),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 256_000,
							price = Price(
								amount = BigDecimal(28),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						)
					),
				),
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = true,
				supportsJsonOutput = true
			),
			SettingItem.Value.Providers.Provider.Model.ModelInfo(
				id = "mimo-v2-flash",
				contextWindow = 256_000,
				maxOutputTokens = 64_000,
				price = SettingItem.Value.Providers.Provider.Model.TokenPrice(
					inputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							price = Price(
								amount = BigDecimal(0.7),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
							cachedPrice = Price(
								amount = BigDecimal(0.07),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
					),
					outputPrice = listOf(
						SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier(
							fromTokens = 0,
							price = Price(
								amount = BigDecimal(2.1),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							),
						),
					),
				),
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = false,
				supportsJsonOutput = true
			)
		),
		errorHandlingRules = listOf(
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 400,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 401,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 402,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 403,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 421,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 429,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.RETRY
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 500,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK
			),
			SettingItem.Value.Providers.Provider.ErrorHandlingRule(
				statusCode = 503,
				strategy = SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy.RETRY
			)
		),
	)
	
	override fun createRequestBody(request: ChatRequest): MiMoRequest {
		val mappedMessages = request.messages.mapNotNull { msg ->
			when (msg) {
				is ChatMessage.SystemMessage -> MiMoMessage.DeveloperMessage(
					content = listOf(MiMoMessage.Content.TextPart(text = msg.content))
				)
				
				is ChatMessage.UserMessage -> MiMoMessage.UserMessage(
					content = listOf(MiMoMessage.Content.TextPart(text = msg.content)) + msg.pictures.orEmpty()
						.map { base64 ->
							MiMoMessage.Content.ImagePart(
								imageUrl = MiMoMessage.Content.ImagePart.Url(base64.value)
							)
						}
				)
				
				is ChatMessage.AssistantMessage -> MiMoMessage.AssistantMessage(
					content = if (msg.content != null) listOf(MiMoMessage.Content.TextPart(text = msg.content)) else null,
					reasoningContent = msg.reasoningContent,
					toolCalls = msg.toolCalls?.map { tc ->
						MiMoToolCall(
							id = tc.id,
							function = MiMoToolCall.Function(
								name = tc.name,
								arguments = tc.arguments
							)
						)
					}
				)
				
				is ChatMessage.ToolMessage -> MiMoMessage.ToolMessage(
					content = listOf(MiMoMessage.Content.TextPart(text = msg.content)),
					toolCallId = msg.toolCallId
				)
				
				is ChatMessage.ErrorMessage -> null
			}
		}
		
		return MiMoRequest(
			model = request.model,
			messages = mappedMessages,
			stream = request.stream,
			tools = request.tools?.map { tool ->
				OpenAiRequest.Tool(
					function = OpenAiRequest.Tool.Function(
						name = tool.name,
						description = tool.description,
						parameters = tool.parameters
					)
				)
			},
			thinking = when (request.thinking) {
				true -> OpenAiRequest.Thinking(OpenAiRequest.Thinking.Type.ENABLED)
				false -> OpenAiRequest.Thinking(OpenAiRequest.Thinking.Type.DISABLED)
				null -> null
			},
			temperature = request.temperature,
			maxCompletionTokens = request.maxTokens,
			topP = request.topP,
			frequencyPenalty = request.frequencyPenalty,
			presencePenalty = request.presencePenalty,
			responseFormat = request.responseFormat,
			toolChoice = if (request.toolCallRequired == true) "auto" else null,
		)
	}
	
	override fun mapToChatResult(response: MiMoResponse): ChatResult {
		val choice = response.choices.firstOrNull()
		val msg = choice?.message
		
		return ChatResult(
			message = ChatMessage.AssistantMessage(
				content = msg?.content,
				reasoningContent = msg?.reasoningContent,
				toolCalls = msg?.toolCalls?.map { tc ->
					ChatMessage.AssistantMessage.ToolCall(
						id = tc.id,
						name = tc.function.name,
						arguments = tc.function.arguments
					)
				},
				createdAt = response.created,
				model = response.model
			),
			usage = response.usage.let { u ->
				Usage(
					totalTokens = u.totalTokens,
					promptTokens = u.promptTokens,
					completionTokens = u.completionTokens,
					reasoningTokens = u.completionTokensDetails?.reasoningTokens,
					cacheHitTokens = u.promptTokensDetails?.cachedTokens,
					cacheMissTokens = if (u.promptTokensDetails?.cachedTokens != null) u.promptTokens - u.promptTokensDetails.cachedTokens else null,
					imageTokens = u.promptTokensDetails?.imageTokens
				)
			},
			finishReason = choice?.finishReason?.toFinishReason()
		)
	}
	
	override fun mapChunkToChatResult(chunk: MiMoStreamChunk): ChatResult {
		val choice = chunk.choices.firstOrNull()
		val delta = choice?.delta
		
		return ChatResult(
			message = ChatMessage.AssistantMessage(
				content = delta?.content,
				reasoningContent = delta?.reasoningContent,
				createdAt = chunk.created,
				model = chunk.model
			),
			usage = chunk.usage?.let { u ->
				Usage(
					totalTokens = u.totalTokens,
					promptTokens = u.promptTokens,
					completionTokens = u.completionTokens,
					reasoningTokens = u.completionTokensDetails?.reasoningTokens,
					cacheHitTokens = u.promptTokensDetails?.cachedTokens,
					cacheMissTokens = if (u.promptTokensDetails?.cachedTokens != null) u.promptTokens - u.promptTokensDetails.cachedTokens else null,
					imageTokens = u.promptTokensDetails?.imageTokens
				)
			},
			finishReason = choice?.finishReason?.toFinishReason()
		)
	}
	
	private fun MiMoFinishReason.toFinishReason() = ChatResult.FinishReason(
		reason = value,
		type = when (this) {
			MiMoFinishReason.STOP -> ChatResult.FinishReason.Type.STOP
			MiMoFinishReason.TOOL_CALLS -> ChatResult.FinishReason.Type.TOOL
			MiMoFinishReason.CONTENT_FILTER -> ChatResult.FinishReason.Type.FILTER
			MiMoFinishReason.LENGTH -> ChatResult.FinishReason.Type.LENGTH
			MiMoFinishReason.REPETITION_TRUNCATION -> ChatResult.FinishReason.Type.ERROR
		}
	)
	
	override fun extractToolCalls(chunk: MiMoStreamChunk): List<ToolCallFragment>? {
		return chunk.choices.firstOrNull()?.delta?.toolCalls?.map { tc ->
			ToolCallFragment(
				index = tc.index,
				id = tc.id,
				name = tc.function?.name,
				arguments = tc.function?.arguments
			)
		}
	}
}
