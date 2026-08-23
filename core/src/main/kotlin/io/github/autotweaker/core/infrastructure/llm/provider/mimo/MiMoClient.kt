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

package io.github.autotweaker.core.infrastructure.llm.provider.mimo

import com.google.auto.service.AutoService
import io.github.autotweaker.api.DataUrl
import io.github.autotweaker.api.ObjectStorable
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.infrastructure.llm.openai.*
import io.ktor.util.reflect.*
import kotlinx.serialization.serializer

@AutoService(LlmClient::class)
class MiMoClient : AbstractOpenAiClient<MiMoRequest, MiMoResponse, MiMoStreamChunk>(
	requestTypeInfo = typeInfo<MiMoRequest>(),
	responseTypeInfo = typeInfo<MiMoResponse>(),
	chunkSerializer = serializer<MiMoStreamChunk>(),
), ObjectStorable {
	override val providerInfo: LlmClient.ProviderInfo = LlmClient.ProviderInfo(
		name = "mimo",
		baseUrl = "https://api.xiaomimimo.com/v1".toUrl(),
		models = listOf(
			ModelData.ModelInfo(
				modelId = "mimo-v2.5-pro",
				contextWindow = 1_000_000,
				maxOutputTokens = 128_000,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsJsonOutput = true
			),
			ModelData.ModelInfo(
				modelId = "mimo-v2.5",
				contextWindow = 1_000_000,
				maxOutputTokens = 128_000,
				supportsStreaming = true,
				supportsToolCalls = true,
				supportsReasoning = true,
				supportsJsonOutput = true,
				supportsImage = true,
				supportsAudio = true,
				supportsVideo = true
			),
		),
		errorHandlingRules = ProviderData.ErrorHandlingRule.build(
			400 to RecoveryStrategy.FALLBACK,
			401 to RecoveryStrategy.PROVIDER_FALLBACK,
			402 to RecoveryStrategy.PROVIDER_FALLBACK,
			403 to RecoveryStrategy.PROVIDER_FALLBACK,
			404 to RecoveryStrategy.FALLBACK,
			421 to RecoveryStrategy.PROVIDER_FALLBACK,
			429 to RecoveryStrategy.RETRY,
			500 to RecoveryStrategy.PROVIDER_FALLBACK,
			503 to RecoveryStrategy.RETRY
		),
	)
	
	override suspend fun ChatRequest.transform(): MiMoRequest {
		var mappedMessages = messages.map { msg ->
			when (msg) {
				is ChatMessage.User -> MiMoMessage.UserMessage(
					content = msg.content.mapNotNull {
						when (it) {
							is ContentPart.Text -> MiMoMessage.Content.TextPart(it.content)
							is ContentPart.Audio -> MiMoMessage.Content.AudioPart(
								DataUrl(it.mimeType, it.data) ?: return@mapNotNull null
							)
							
							is ContentPart.AudioUrl -> MiMoMessage.Content.AudioPart(it.url.toString())
							is ContentPart.Image -> MiMoMessage.Content.ImagePart(
								DataUrl(it.mimeType, it.data) ?: return@mapNotNull null
							)
							
							is ContentPart.ImageUrl -> MiMoMessage.Content.ImagePart(it.url.toString())
							is ContentPart.Video -> MiMoMessage.Content.VideoPart(
								DataUrl(it.mimeType, it.data) ?: return@mapNotNull null
							)
							
							is ContentPart.VideoUrl -> MiMoMessage.Content.VideoPart(it.url.toString())
						}
					}
				)
				
				is ChatMessage.Assistant -> MiMoMessage.AssistantMessage(
					content = msg.content,
					reasoningContent = msg.reasoningContent,
					toolCalls = msg.toolCalls?.map {
						OpenAiToolCall(
							id = it.id, function = OpenAiToolCall.Function(
								name = it.name, arguments = it.arguments
							)
						)
					})
				
				is ChatMessage.ToolResult -> MiMoMessage.ToolMessage(
					content = msg.content, toolCallId = msg.toolCallId
				)
			}
		}
		
		instructions?.let {
			mappedMessages = listOf(
				MiMoMessage.DeveloperMessage(
					content = it
				)
			) + mappedMessages
		}
		
		return MiMoRequest(
			model = model,
			messages = mappedMessages,
			stream = stream,
			tools = tools?.transform(),
			thinking = reasoning?.let { OpenAiThinking(it) },
			temperature = temperature,
			maxCompletionTokens = maxTokens,
			responseFormat = if (jsonOutput == true) OpenAiResponseFormat() else null,
		)
	}
	
	override fun MiMoResponse.transform(): ChatResult {
		val choice = choices.firstOrNull()
		val msg = choice?.message
		
		return ChatResult.Assembled(
			message = ChatMessage.Assistant(
				content = msg?.content,
				reasoningContent = msg?.reasoningContent,
				toolCalls = msg?.toolCalls?.transform(),
				timestamp = created,
			),
			usage = usage.transform(),
		)
	}
	
	override fun MiMoStreamChunk.transform(): ChatResult.Chunk {
		val choice = choices.firstOrNull()
		val delta = choice?.delta
		
		return ChatResult.Chunk(
			content = delta?.content,
			reasoningContent = delta?.reasoningContent,
			toolCalls = delta?.toolCalls?.transform()
		)
	}
	
	override fun MiMoStreamChunk.timestamp() = created
	
	override fun MiMoStreamChunk.usage() = usage?.transform()
	
	private fun MiMoUsage.transform() = Usage(
		promptTokens = promptTokens,
		completionTokens = completionTokens,
		reasoningTokens = completionTokensDetails?.reasoningTokens,
		cacheHitTokens = promptTokensDetails?.cachedTokens,
	)
}
