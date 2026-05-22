/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.llm.provider.deepseek

import com.google.auto.service.AutoService
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.core.llm.base.openai.AbstractOpenAiClient
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import io.ktor.util.reflect.*
import kotlinx.serialization.serializer
import java.math.BigDecimal
import java.util.*

@AutoService(LlmClient::class)
class DeepSeekClient : AbstractOpenAiClient<DeepSeekRequest, DeepSeekResponse, DeepSeekStreamChunk>(
	requestTypeInfo = typeInfo<DeepSeekRequest>(),
	responseTypeInfo = typeInfo<DeepSeekResponse>(),
	chunkSerializer = serializer<DeepSeekStreamChunk>(),
) {
	override val providerInfo: LlmClient.ProviderInfo = LlmClient.ProviderInfo(
		name = "deepseek", baseUrl = Url("https://api.deepseek.com/v1"), models = listOf(
			ModelData.ModelInfo(
				modelId = "deepseek-v4-flash",
				contextWindow = 100_0000,
				maxOutputTokens = 384_000,
				price = ModelData.TokenPrice(
					inputPrice = listOf(
						ModelData.TokenPrice.PriceTier(
							fromTokens = 0, price = Price(
								amount = BigDecimal("1"), currency = Currency.getInstance(Locale.CHINA), unit = 100_0000
							), cachedPrice = Price(
								amount = BigDecimal("0.02"),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							)
						)
					),
					outputPrice = listOf(
						ModelData.TokenPrice.PriceTier(
							fromTokens = 0, price = Price(
								amount = BigDecimal("2"), currency = Currency.getInstance(Locale.CHINA), unit = 100_0000
							)
						)
					),
				),
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = false,
				supportsJsonOutput = true
			), ModelData.ModelInfo(
				modelId = "deepseek-v4-pro",
				contextWindow = 100_0000,
				maxOutputTokens = 384_000,
				price = ModelData.TokenPrice(
					inputPrice = listOf(
						ModelData.TokenPrice.PriceTier(
							fromTokens = 0, price = Price(
								amount = BigDecimal("3"), currency = Currency.getInstance(Locale.CHINA), unit = 100_0000
							), cachedPrice = Price(
								amount = BigDecimal("0.025"),
								currency = Currency.getInstance(Locale.CHINA),
								unit = 100_0000
							)
						)
					),
					outputPrice = listOf(
						ModelData.TokenPrice.PriceTier(
							fromTokens = 0, price = Price(
								amount = BigDecimal("6"), currency = Currency.getInstance(Locale.CHINA), unit = 100_0000
							)
						)
					),
				),
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsImage = false,
				supportsJsonOutput = true
			)
		), errorHandlingRules = listOf(
			ProviderData.ErrorHandlingRule(
				statusCode = 400,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.FALLBACK,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 401,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 402,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 422,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 429,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 500,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.PROVIDER_FALLBACK,
			),
			ProviderData.ErrorHandlingRule(
				statusCode = 503,
				strategy = ProviderData.ErrorHandlingRule.RecoveryStrategy.RETRY,
			),
		)
	)
	
	override fun createRequestBody(request: ChatRequest): DeepSeekRequest {
		val mappedMessages = request.messages.mapNotNull { msg ->
			when (msg) {
				is ChatMessage.SystemMessage -> DeepSeekMessage.SystemMessage(
					content = msg.content
				)
				
				is ChatMessage.UserMessage -> DeepSeekMessage.UserMessage(
					content = msg.content
				)
				
				is ChatMessage.AssistantMessage -> DeepSeekMessage.AssistantMessage(
					content = msg.content,
					reasoningContent = msg.reasoningContent,
					toolCalls = msg.toolCalls?.map { tc ->
						DeepSeekMessage.AssistantMessage.ToolCall(
							id = tc.id, function = DeepSeekMessage.AssistantMessage.ToolCall.Function(
								name = tc.name, arguments = tc.arguments
							)
						)
					})
				
				is ChatMessage.ToolMessage -> DeepSeekMessage.ToolMessage(
					content = msg.content, toolCallId = msg.toolCallId
				)
				
				is ChatMessage.ErrorMessage -> null
			}
		}
		
		
		return DeepSeekRequest(
			model = request.model,
			messages = mappedMessages,
			stream = request.stream,
			streamOptions = if (request.stream) {
				DeepSeekRequest.StreamOptions(includeUsage = true)
			} else null,
			tools = request.tools?.map { tool ->
				OpenAiRequest.Tool(
					function = OpenAiRequest.Tool.Function(
						name = tool.name, description = tool.description, parameters = tool.parameters
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
			toolChoice = when (request.toolCallRequired) {
				true -> ToolChoice.REQUIRED
				false -> ToolChoice.NONE
				null -> null
			},
		)
	}
	
	override fun mapToChatResult(response: DeepSeekResponse): ChatResult {
		val choice = response.choices.firstOrNull()
		val msg = choice?.message
		
		return ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = msg?.content,
				reasoningContent = msg?.reasoningContent,
				toolCalls = msg?.toolCalls?.map { tc ->
					ChatMessage.AssistantMessage.ToolCall(
						id = tc.id, name = tc.function.name, arguments = tc.function.arguments
					)
				},
				createdAt = response.created,
				model = response.model
			), usage = response.usage.let { u ->
				Usage(
					promptTokens = u.promptTokens,
					completionTokens = u.completionTokens,
					reasoningTokens = u.completionTokensDetails?.reasoningTokens,
					cacheHitTokens = u.promptCacheHitTokens,
				)
			}, finishReason = choice?.finishReason?.toFinishReason()
		)
	}
	
	override fun mapChunkToChatResult(chunk: DeepSeekStreamChunk): ChatResult {
		val choice = chunk.choices.firstOrNull()
		val delta = choice?.delta
		
		return ChatResult.Chunk(
			message = ChatMessage.AssistantMessage(
				content = delta?.content,
				reasoningContent = delta?.reasoningContent,
				createdAt = chunk.created,
				model = chunk.model
			), usage = chunk.usage?.let { u ->
				Usage(
					promptTokens = u.promptTokens,
					completionTokens = u.completionTokens,
					reasoningTokens = u.completionTokensDetails?.reasoningTokens,
					cacheHitTokens = u.promptCacheHitTokens,
				)
			}, finishReason = choice?.finishReason?.toFinishReason()
		)
	}
	
	private fun DeepSeekFinishReason.toFinishReason() = ChatResult.FinishReason(
		reason = value, type = when (this) {
			DeepSeekFinishReason.STOP -> ChatResult.FinishReason.Type.STOP
			DeepSeekFinishReason.TOOL_CALLS -> ChatResult.FinishReason.Type.TOOL
			DeepSeekFinishReason.CONTENT_FILTER -> ChatResult.FinishReason.Type.FILTER
			DeepSeekFinishReason.LENGTH -> ChatResult.FinishReason.Type.LENGTH
			DeepSeekFinishReason.INSUFFICIENT_SYSTEM_RESOURCE -> ChatResult.FinishReason.Type.ERROR
		}
	)
	
	override fun extractToolCalls(chunk: DeepSeekStreamChunk): List<ChatResult.ChunkToolCall>? {
		return chunk.choices.firstOrNull()?.delta?.toolCalls?.map { tc ->
			ChatResult.ChunkToolCall(
				index = tc.index, id = tc.id, name = tc.function?.name, arguments = tc.function?.arguments
			)
		}
	}
}
