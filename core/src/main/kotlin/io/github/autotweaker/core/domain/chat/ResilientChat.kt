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

package io.github.autotweaker.core.domain.chat

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.exception.ChatRetriesExhaustedException
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ChatRequest.Tool
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.port.LlmGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ResilientChat(
	private val gateway: LlmGateway
) : Loggable {
	fun execute(
		model: RuntimeModel,
		fallbackModels: List<RuntimeModel>?,
		timeout: ChatTimeout? = null,
		
		instructions: String? = null,
		messages: List<ChatMessage>,
		reasoning: ReasoningEffort? = null,
		stream: Boolean = false,
		
		maxTokens: Int? = null,
		tools: List<Tool>? = null,
		
		temperature: Double? = null,
		jsonOutput: Boolean? = null,
	): Flow<LlmResult> = flow {
		val maxRetries = ResilientChatSettings.MaxRetries().get()
		val llmChatRetries = ResilientChatSettings.LlmChatRetries().get()
		val effectiveTimeout = timeout ?: ChatTimeout(
			requestTimeout = ResilientChatSettings.ChatRequestTimeout().get().seconds,
			connectTimeout = ResilientChatSettings.ChatConnectTimeout().get().seconds,
			streamChunkTimeout = ResilientChatSettings.ChatStreamChunkTimeout().get().seconds,
		)
		
		log.debug(
			"Started chat  provider={}  model={}  candidates={}  maxRetries={}  llmChatRetries={}",
			model.provider.name,
			model.modelInfo.modelId,
			(fallbackModels?.size ?: 0) + 1,
			maxRetries,
			llmChatRetries
		)
		
		fun buildRequest(model: RuntimeModel): ChatRequest {
			val info = model.modelInfo
			val thinkingDisabled = !info.supportsReasoning || reasoning == ReasoningEffort.NONE
			
			return ChatRequest(
				model = info.modelId,
				instructions = instructions,
				messages = messages.map { msg ->
					when (msg) {
						is ChatMessage.User ->
							if (info.supportsImage && info.supportsAudio && info.supportsVideo) msg
							else msg.copy(
								content = msg.content.filter { part ->
									when (part) {
										is ContentPart.Image, is ContentPart.ImageUrl -> info.supportsImage
										is ContentPart.Audio, is ContentPart.AudioUrl -> info.supportsAudio
										is ContentPart.Video, is ContentPart.VideoUrl -> info.supportsVideo
										else -> true
									}
								}
							)
						
						is ChatMessage.Assistant ->
							when {
								thinkingDisabled && msg.reasoningContent != null -> msg.copy(
									reasoningContent = null,
									content = msg.content.orEmpty()
								)
								
								!thinkingDisabled && msg.reasoningContent == null -> msg.copy(
									reasoningContent = "",
									content = msg.content.orEmpty()
								)
								
								msg.content == null -> msg.copy(content = "")
								else -> msg
							}
						
						else -> msg
					}
				},
				tools = tools,
				stream = stream && info.supportsStreaming,
				reasoning = if (info.supportsReasoning) reasoning else null,
				temperature = temperature ?: model.config?.temperature,
				maxTokens = maxTokens ?: model.config?.maxOutputTokens,
				jsonOutput = jsonOutput
			)
		}
		
		var attempts = 0
		
		suspend fun attempt(target: RuntimeModel): Pair<Int?, Boolean> {
			val chatRequest = buildRequest(target)
			val results = gateway.send(
				request = chatRequest,
				apiKey = target.provider.apiKey,
				baseUrl = target.provider.baseUrl,
				providerType = target.provider.name,
				timeout = effectiveTimeout,
			)
			
			var statusCode: Int? = null
			var hasError = false
			
			results.collect { result ->
				if (result is ChatResult.Failed) {
					log.warn(
						"Failed LLM chat, maybe retry  provider={}  model={}  statusCode={}  reason={}  exception={}",
						target.provider.name,
						target.modelInfo.modelId,
						result.statusCode,
						result.message,
						result.exception?.message()
					)
					emit(LlmResult(result, model = target.id))
					statusCode = result.statusCode
					hasError = true
				} else emit(LlmResult(result.normalizeEmpty(), model = target.id))
			}
			attempts++
			
			return Pair(statusCode, hasError)
		}
		
		for (round in 0..llmChatRetries) {
			var candidates = buildList {
				add(model)
				addAll(fallbackModels.orEmpty())
			}
			
			while (candidates.isNotEmpty()) {
				val current = candidates.first()
				val rules = current.provider.errorHandlingRules
				
				suspend fun handle(result: Pair<Int?, Boolean>, retriesUsed: Int): Boolean {
					val (statusCode, hasError) = result
					if (!hasError) return true
					
					when (rules.find { it.statusCode == statusCode }?.strategy) {
						RecoveryStrategy.RETRY, null -> {
							if (retriesUsed >= maxRetries - 1) {
								log.debug(
									"Exhausted chat retries  model={}  strategy=RETRY", current.modelInfo.modelId
								)
								candidates = candidates.drop(1)
								return false
							}
							log.debug(
								"Retried chat  model={}  attempt={}  strategy=RETRY  statusCode={}",
								current.modelInfo.modelId,
								retriesUsed + 1,
								statusCode
							)
							val baseDelay = ResilientChatSettings.RetryBaseDelaySeconds().get()
							val maxDelay = ResilientChatSettings.MaxRetryDelaySeconds().get()
							val jitterEnabled = ResilientChatSettings.RetryJitterEnabled().get()
							val scale = 1L shl retriesUsed
							val capped = if (scale < 0) maxDelay.seconds
							else minOf(scale.seconds * baseDelay, maxDelay.seconds)
							val finalDelay = if (jitterEnabled)
								Random.nextLong(capped.inWholeMilliseconds + 1).milliseconds
							else capped
							delay(finalDelay)
							
							return handle(attempt(current), retriesUsed + 1)
						}
						
						RecoveryStrategy.CONTEXT_FALLBACK -> {
							log.debug(
								"Fell back to larger context window  currentModel={}  statusCode={}",
								current.modelInfo.modelId,
								statusCode
							)
							candidates =
								candidates.filter { it.modelInfo.contextWindow > current.modelInfo.contextWindow }
							return false
						}
						
						RecoveryStrategy.PROVIDER_FALLBACK -> {
							log.debug(
								"Fell back to different provider  currentModel={}  currentProvider={}  statusCode={}",
								current.modelInfo.modelId,
								current.provider.id,
								statusCode
							)
							candidates = candidates.filter { it.provider.id != current.provider.id }
							return false
						}
						
						RecoveryStrategy.FALLBACK -> {
							log.debug(
								"Fell back to next model  currentModel={}  statusCode={}",
								current.modelInfo.modelId,
								statusCode
							)
							candidates = candidates.drop(1)
							return false
						}
					}
				}
				
				if (handle(attempt(current), 0)) return@flow
			}
			
			if (round < llmChatRetries)
				log.info("Exhausted all candidate models and restarted  round={}", round + 1)
		}
		
		log.warn("Exhausted all LLM chat retries  attempts={}", attempts)
		throw ChatRetriesExhaustedException(attempts)
	}
	
	private fun ChatResult.normalizeEmpty(): ChatResult = when (this) {
		is ChatResult.Chunk -> copy(
			reasoningContent = reasoningContent?.orNull(),
			content = content?.orNull()
		)
		
		is ChatResult.Assembled -> copy(
			message = message.copy(
				reasoningContent = message.reasoningContent?.orNull(),
				content = message.content?.orNull()
			)
		)
		
		else -> this
	}
}
