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

package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.llm.LlmClientLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal object ResilientChat {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	internal fun execute(
		model: Model,
		fallbackModels: List<Model>?,
		request: ChatRequest,
		service: SettingService,
	): Flow<ResilientChatResult> = flow {
		val maxRetries = service.get(ResilientChatSettings.MaxRetries()).value
		require(maxRetries > 0) { "maxRetries must be positive" }
		
		var candidates = buildList {
			add(model)
			addAll(fallbackModels.orEmpty())
		}
		
		logger.debug(
			"Chat started  provider={}  model={}  candidates={}  maxRetries={}",
			model.provider.name, model.modelInfo.modelId, candidates.size, maxRetries
		)
		
		// 图像兼容性预处理：存在支持图像的模型时，屏蔽所有不支持的
		if (request.messages.any { it is ChatMessage.UserMessage && !it.pictures.isNullOrEmpty() }) {
			candidates = candidates.filter { it.modelInfo.supportsImage }.ifEmpty { candidates }
		}
		
		var lastModelId: String? = null
		while (candidates.isNotEmpty()) {
			val current = candidates.first()
			lastModelId = current.modelInfo.modelId
			val rules = current.provider.errorHandlingRules
			
			for (retryAttempt in 0 until maxRetries) {
				val chatRequest = request.adapt(current)
				val client = LlmClientLoader.load(current.provider.name)
				val results = client.chat(
					request = chatRequest,
					apiKey = current.provider.apiKey,
					baseUrl = current.provider.baseUrl,
				)
				
				var lastError: ChatResult? = null
				var lastStatusCode: Int? = null
				
				results.collect { result ->
					val msg = result.message
					if (msg is ChatMessage.ErrorMessage) {
						lastError = result
						lastStatusCode = msg.statusCode
					} else {
						emit(ResilientChatResult(result.normalizeEmptyStrings(), retrying = null))
					}
				}
				
				val error = lastError ?: return@flow
				
				// 匹配错误处理规则
				val matchedRule = if (lastStatusCode != null) {
					rules.find { it.statusCode == lastStatusCode }
				} else {
					null
				}
				
				when (matchedRule?.strategy) {
					RecoveryStrategy.RETRY -> {
						if (retryAttempt < maxRetries - 1) {
							logger.debug(
								"Chat retried  model={}  attempt={}  strategy=RETRY  statusCode={}",
								current.modelInfo.modelId, retryAttempt + 1, lastStatusCode
							)
							emit(ResilientChatResult(error, retrying = current))
							val baseDelay = service.get(ResilientChatSettings.RetryBaseDelaySeconds()).value
							val maxDelay = service.get(ResilientChatSettings.MaxRetryDelaySeconds()).value
							val jitterEnabled = service.get(ResilientChatSettings.RetryJitterEnabled()).value
							val capped = minOf(baseDelay.seconds * (1 shl retryAttempt), maxDelay.seconds)
							val finalDelay = if (jitterEnabled) {
								Random.nextLong(capped.inWholeMilliseconds + 1).milliseconds
							} else capped
							delay(finalDelay)
							continue
						}
						logger.debug(
							"Chat retries exhausted  model={}  strategy=RETRY", current.modelInfo.modelId
						)
						candidates = candidates.drop(1)
					}
					
					RecoveryStrategy.CONTEXT_FALLBACK -> {
						logger.debug(
							"Fell back to larger context window  model={}  statusCode={}",
							current.modelInfo.modelId, lastStatusCode
						)
						candidates = candidates.filter { it.modelInfo.contextWindow > current.modelInfo.contextWindow }
					}
					
					RecoveryStrategy.PROVIDER_FALLBACK -> {
						logger.debug(
							"Fell back to different provider  model={}  provider={}  statusCode={}",
							current.modelInfo.modelId, current.provider.name, lastStatusCode
						)
						candidates = candidates.filter { it.provider.name != current.provider.name }
					}
					
					RecoveryStrategy.FALLBACK, null -> {
						logger.debug(
							"Fell back to next model  model={}  statusCode={}",
							current.modelInfo.modelId, lastStatusCode
						)
						candidates = candidates.drop(1)
					}
				}
				
				emit(ResilientChatResult(error, retrying = candidates.firstOrNull()))
				
				break // 跳出当前模型的重试循环，回到外层取下一个候选
			}
		}
		
		logger.warn("All candidate models exhausted  lastModel={}  candidates={}", lastModelId, 0)
		throw IllegalStateException("All candidate models exhausted without success")
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
	
	private fun ChatRequest.adapt(model: Model): ChatRequest {
		val stripPictures = !model.modelInfo.supportsImage &&
				messages.any { it is ChatMessage.UserMessage && !it.pictures.isNullOrEmpty() }
		val stripThinking = !model.modelInfo.supportsReasoning && thinking == true
		val shouldStripReasoning = !model.modelInfo.supportsReasoning || thinking != true
		
		return copy(
			model = model.modelInfo.modelId,
			stream = stream && model.modelInfo.supportsStreaming,
			thinking = if (stripThinking) null else thinking,
			temperature = model.config?.temperature,
			maxTokens = model.config?.maxTokens,
			messages = messages.mapIndexed { _, msg ->
				var result = msg
				if (stripPictures && result is ChatMessage.UserMessage) {
					result = result.copy(pictures = null)
				}
				if (result is ChatMessage.AssistantMessage) {
					result = when {
						shouldStripReasoning -> result.copy(reasoningContent = null)
						result.reasoningContent == null -> result.copy(reasoningContent = "")
						else -> result
					}
				}
				result
			},
		)
	}
}
