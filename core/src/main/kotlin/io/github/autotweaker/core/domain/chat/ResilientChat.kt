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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.LlmGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object ResilientChat {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private lateinit var gateway: LlmGateway
	private lateinit var settings: SettingService
	
	fun init(gateway: LlmGateway, settings: SettingService) {
		this.gateway = gateway
		this.settings = settings
	}
	
	private class StatusCodeNullException : Exception()
	
	fun execute(
		model: Model,
		fallbackModels: List<Model>?,
		messages: List<ChatMessage>,
		tools: List<ChatRequest.Tool>? = null,
		responseFormat: ChatRequest.ResponseFormat? = null,
		stream: Boolean = false,
		thinking: Boolean? = null,
	): Flow<CoreLlmResult> = flow {
		val maxRetries = settings.get(ResilientChatSettings.MaxRetries()).value
		val llmChatRetries = settings.get(ResilientChatSettings.LlmChatRetries()).value
		
		logger.debug(
			"Chat started  provider={}  model={}  candidates={}  maxRetries={}  llmChatRetries={}",
			model.provider.name,
			model.modelInfo.modelId,
			(fallbackModels?.size ?: 0) + 1,
			maxRetries,
			llmChatRetries
		)
		
		val userMsg = messages.filterIsInstance<ChatMessage.UserMessage>()
		
		suspend fun attempt(target: Model): Int? {
			val chatRequest = buildRequest(target, messages, tools, responseFormat, stream, thinking)
			val results = gateway.send(
				request = chatRequest,
				apiKey = target.provider.apiKey,
				baseUrl = target.provider.baseUrl,
				providerType = target.provider.name,
			)
			
			var errorStatusCode: Int? = null
			
			results.collect { result ->
				val msg = result.message
				if (msg is ChatMessage.ErrorMessage) {
					emit(CoreLlmResult(result, model = target.id))
					val sc = msg.statusCode ?: throw StatusCodeNullException()
					errorStatusCode = sc
				} else {
					emit(CoreLlmResult(result.normalizeEmptyStrings(), model = target.id))
				}
			}
			
			return errorStatusCode
		}
		
		for (round in 0..llmChatRetries) {
			var candidates = buildList {
				add(model)
				addAll(fallbackModels.orEmpty())
			}
			if (userMsg.any { !it.pictures.isNullOrEmpty() } && candidates.any { it.modelInfo.supportsImage }) {
				candidates =
					candidates.filter { it.modelInfo.supportsImage } + candidates.filter { !it.modelInfo.supportsImage }
			}
			
			while (candidates.isNotEmpty()) {
				val current = candidates.first()
				val rules = current.provider.errorHandlingRules
				
				try {
					suspend fun handle(statusCode: Int, retriesUsed: Int): Boolean {
						when (rules.find { it.statusCode == statusCode }?.strategy) {
							RecoveryStrategy.RETRY -> {
								if (retriesUsed >= maxRetries - 1) {
									logger.debug(
										"Chat retries exhausted  model={}  strategy=RETRY", current.modelInfo.modelId
									)
									candidates = candidates.drop(1)
									return false
								}
								logger.debug(
									"Chat retried  model={}  attempt={}  strategy=RETRY  statusCode={}",
									current.modelInfo.modelId,
									retriesUsed + 1,
									statusCode
								)
								val baseDelay = settings.get(ResilientChatSettings.RetryBaseDelaySeconds()).value
								val maxDelay = settings.get(ResilientChatSettings.MaxRetryDelaySeconds()).value
								val jitterEnabled = settings.get(ResilientChatSettings.RetryJitterEnabled()).value
								val capped = minOf(baseDelay.seconds * (1 shl retriesUsed), maxDelay.seconds)
								val finalDelay = if (jitterEnabled) {
									Random.nextLong(capped.inWholeMilliseconds + 1).milliseconds
								} else capped
								delay(finalDelay)
								
								val nextCode = attempt(current) ?: return true
								return handle(nextCode, retriesUsed + 1)
							}
							
							RecoveryStrategy.CONTEXT_FALLBACK -> {
								logger.debug(
									"Fell back to larger context window  model={}  statusCode={}",
									current.modelInfo.modelId,
									statusCode
								)
								candidates =
									candidates.filter { it.modelInfo.contextWindow > current.modelInfo.contextWindow }
								return false
							}
							
							RecoveryStrategy.PROVIDER_FALLBACK -> {
								logger.debug(
									"Fell back to different provider  model={}  provider={}  statusCode={}",
									current.modelInfo.modelId,
									current.provider.id,
									statusCode
								)
								candidates = candidates.filter { it.provider.id != current.provider.id }
								return false
							}
							
							RecoveryStrategy.FALLBACK, null -> {
								logger.debug(
									"Fell back to next model  model={}  statusCode={}",
									current.modelInfo.modelId,
									statusCode
								)
								candidates = candidates.drop(1)
								return false
							}
						}
					}
					
					val firstCode = attempt(current) ?: return@flow
					if (handle(firstCode, 0)) return@flow
				} catch (_: StatusCodeNullException) {
					candidates = candidates.drop(1)
				}
			}
			
			if (round < llmChatRetries) {
				logger.debug("All candidate models exhausted  restarting  round={}", round + 1)
			}
		}
		
		logger.warn("All LLM chat retries exhausted")
		throw IllegalStateException("All LLM chat retries exhausted without success")
	}
	
	private fun ChatResult.normalizeEmptyStrings(): ChatResult {
		val msg = message
		if (msg !is ChatMessage.AssistantMessage) return this
		val content = msg.content
		val reasoningContent = msg.reasoningContent
		if (content != "" && reasoningContent != "") return this
		val normalized = msg.copy(
			content = if (content == "") null else content,
			reasoningContent = if (reasoningContent == "") null else reasoningContent
		)
		return when (this) {
			is ChatResult.Chunk -> copy(message = normalized)
			is ChatResult.Assembled -> copy(message = normalized)
		}
	}
	
	private fun buildRequest(
		model: Model,
		messages: List<ChatMessage>,
		tools: List<ChatRequest.Tool>?,
		responseFormat: ChatRequest.ResponseFormat?,
		stream: Boolean,
		thinking: Boolean?,
	): ChatRequest {
		val stripPictures = !model.modelInfo.supportsImage
		val stripThinking = !model.modelInfo.supportsReasoning && thinking == true
		val shouldStripReasoning = !model.modelInfo.supportsReasoning || thinking != true
		
		return ChatRequest(
			model = model.modelInfo.modelId,
			messages = messages.map { msg ->
				var result = msg
				if (stripPictures && result is ChatMessage.UserMessage) {
					result = result.copy(pictures = null)
				}
				if (result is ChatMessage.AssistantMessage) {
					result = when {
						shouldStripReasoning -> result.copy(reasoningContent = null)
						!shouldStripReasoning && result.reasoningContent == null -> result.copy(reasoningContent = "</think>")
						else -> result
					}
				}
				result
			},
			tools = tools,
			responseFormat = responseFormat,
			stream = stream && model.modelInfo.supportsStreaming,
			thinking = if (stripThinking) null else thinking,
			temperature = model.config?.temperature,
			maxTokens = model.config?.maxTokens,
		)
	}
}
