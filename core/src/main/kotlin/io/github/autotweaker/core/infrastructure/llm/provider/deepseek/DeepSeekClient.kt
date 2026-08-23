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

package io.github.autotweaker.core.infrastructure.llm.provider.deepseek

import com.google.auto.service.AutoService
import io.github.autotweaker.api.DataUrl
import io.github.autotweaker.api.ObjectStorable
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.infrastructure.llm.openai.AbstractOpenAiClient
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiResponseFormat
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiThinking
import io.github.autotweaker.core.infrastructure.llm.openai.transform
import io.ktor.util.reflect.*

@AutoService(LlmClient::class)
class DeepSeekClient : AbstractOpenAiClient<DeepSeekRequest, DeepSeekResponse, DeepSeekStreamChunk>(
	requestTypeInfo = typeInfo<DeepSeekRequest>(),
	responseTypeInfo = typeInfo<DeepSeekResponse>(),
	chunkSerializer = DeepSeekStreamChunk.serializer(),
), ObjectStorable {
	override val providerInfo = LlmClient.ProviderInfo(
		name = "deepseek", baseUrl = "https://api.deepseek.com/v1".toUrl(), models = listOf(
			ModelData.ModelInfo(
				modelId = "deepseek-v4-flash",
				contextWindow = 1_000_000,
				maxOutputTokens = 384_000,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsJsonOutput = true,
			), ModelData.ModelInfo(
				modelId = "deepseek-v4-pro",
				contextWindow = 1_000_000,
				maxOutputTokens = 384_000,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsJsonOutput = true,
			)
		), errorHandlingRules = ProviderData.ErrorHandlingRule.build(
			400 to RecoveryStrategy.FALLBACK,
			401 to RecoveryStrategy.PROVIDER_FALLBACK,
			402 to RecoveryStrategy.PROVIDER_FALLBACK,
			422 to RecoveryStrategy.FALLBACK,
			429 to RecoveryStrategy.RETRY,
			500 to RecoveryStrategy.PROVIDER_FALLBACK,
			503 to RecoveryStrategy.RETRY
		)
	)
	
	override suspend fun ChatRequest.transform(): DeepSeekRequest {
		var mappedMessages = messages.map { msg ->
			when (msg) {
				is ChatMessage.User -> DeepSeekMessage.UserMessage(
					content = msg.content.mapNotNull {
						when (it) {
							is ContentPart.Text -> DeepSeekMessage.UserMessage.Part.Text(it.content)
							is ContentPart.Image -> DeepSeekMessage.UserMessage.Part.Image(
								DataUrl(it.mimeType, it.data) ?: return@mapNotNull null
							)
							
							is ContentPart.ImageUrl -> DeepSeekMessage.UserMessage.Part.Image(it.url.toString())
							else -> null
						}
					}
				)
				
				is ChatMessage.Assistant -> DeepSeekMessage.AssistantMessage(
					content = msg.content,
					reasoningContent = msg.reasoningContent,
					toolCalls = msg.toolCalls?.transform()
				)
				
				is ChatMessage.ToolResult -> DeepSeekMessage.ToolMessage(
					content = msg.content, toolCallId = msg.toolCallId
				)
			}
		}
		
		instructions?.let {
			mappedMessages = listOf(
				DeepSeekMessage.SystemMessage(
					content = it
				)
			) + mappedMessages
		}
		
		return DeepSeekRequest(
			model = model,
			messages = mappedMessages,
			stream = stream,
			streamOptions = if (stream) DeepSeekRequest.StreamOptions() else null,
			tools = tools?.transform(),
			thinking = reasoning?.let { OpenAiThinking(it) },
			reasoningEffort = when (reasoning) {
				ReasoningEffort.NONE -> null
				ReasoningEffort.MINIMAL -> DeepSeekRequest.Effort.LOW
				ReasoningEffort.LOW -> DeepSeekRequest.Effort.LOW
				ReasoningEffort.MEDIUM -> DeepSeekRequest.Effort.HIGH
				ReasoningEffort.HIGH -> DeepSeekRequest.Effort.HIGH
				ReasoningEffort.XHIGH -> DeepSeekRequest.Effort.MAX
				null -> null
			},
			temperature = temperature,
			maxTokens = maxTokens,
			responseFormat = if (jsonOutput == true) OpenAiResponseFormat() else null,
			toolChoice = null
		)
	}
	
	override fun DeepSeekResponse.transform(): ChatResult {
		val choice = choices.firstOrNull()
		val msg = choice?.message
		
		return ChatResult.Assembled(
			message = ChatMessage.Assistant(
				content = msg?.content,
				reasoningContent = msg?.reasoningContent,
				toolCalls = msg?.toolCalls?.transform(),
				timestamp = created,
			),
			usage = usage.transform()
		)
	}
	
	override fun DeepSeekStreamChunk.transform(): ChatResult.Chunk {
		val choice = choices.firstOrNull()
		val delta = choice?.delta
		
		return ChatResult.Chunk(
			content = delta?.content,
			reasoningContent = delta?.reasoningContent,
			toolCalls = delta?.toolCalls?.transform()
		)
	}
	
	override fun DeepSeekStreamChunk.timestamp() = created
	
	override fun DeepSeekStreamChunk.usage() = usage?.transform()
	
	private fun DeepSeekUsage.transform() = Usage(
		promptTokens = promptTokens,
		completionTokens = completionTokens,
		reasoningTokens = completionTokensDetails?.reasoningTokens,
		cacheHitTokens = promptCacheHitTokens ?: promptTokensDetails?.cachedTokens,
	)
}
