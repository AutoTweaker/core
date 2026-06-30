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

package io.github.autotweaker.core.infrastructure.llm.openai

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

abstract class AbstractOpenAiClient<Request : OpenAiRequest, Response : OpenAiResponse, Chunk : OpenAiStreamChunk>(
	private val requestTypeInfo: TypeInfo,
	private val responseTypeInfo: TypeInfo,
	private val chunkSerializer: KSerializer<Chunk>,
) : LlmClient, Loggable, Traceable {
	companion object {
		private val json: Json = Json {
			ignoreUnknownKeys = true
			isLenient = true
			explicitNulls = false
			encodeDefaults = true
			coerceInputValues = true
		}
		
		private val sharedHttpClient: HttpClient = HttpClient {
			install(ContentNegotiation) {
				json(json)
			}
			install(HttpTimeout)
		}
		
		fun close() {
			sharedHttpClient.close()
		}
		
		private fun buildToolCalls(
			pendingToolCalls: Map<Int, PendingToolCall>
		): List<ChatMessage.AssistantMessage.ToolCall>? {
			if (pendingToolCalls.isEmpty()) return null
			return pendingToolCalls.toSortedMap().values.map { it.toToolCall() }
		}
	}
	
	private class PendingToolCall(
		var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder()
	) {
		fun toToolCall() = ChatMessage.AssistantMessage.ToolCall(id, name, arguments.toString())
	}
	
	protected abstract fun createRequestBody(request: ChatRequest): Request
	protected abstract fun mapToChatResult(response: Response): ChatResult
	protected abstract fun mapChunkToChatResult(chunk: Chunk): ChatResult.Chunk
	protected abstract fun extractToolCalls(chunk: Chunk): List<ChatResult.ChunkToolCall>?
	
	override suspend fun chat(
		request: ChatRequest,
		apiKey: String,
		baseUrl: Url?,
		timeout: ChatTimeout?
	): Flow<ChatResult> = flow {
		val effectiveBaseUrl = baseUrl ?: providerInfo.baseUrl
		trace.catching {
			if (request.stream) {
				streamChat(request, apiKey, effectiveBaseUrl, timeout)
			} else {
				nonStreamChat(request, apiKey, effectiveBaseUrl, timeout)
			}
		}.rethrowCancellation {
			log.debug("Cancelled LLM request  provider={}  model={}", providerInfo.name, request.model)
		}.getOrElse { e ->
			log.error("Failed LLM request execution  provider={}  model={}", providerInfo.name, request.model, e)
			emit(
				ChatResult.Assembled(
					message = ChatMessage.ErrorMessage(
						content = e.message ?: "Unknown error", createdAt = Clock.System.now(), statusCode = null
					),
				)
			)
		}
	}
	
	private suspend fun FlowCollector<ChatResult>.streamChat(
		request: ChatRequest, apiKey: String, baseUrl: Url, timeout: ChatTimeout?
	) {
		sharedHttpClient.preparePost {
			configureRequest(request, apiKey, baseUrl, timeout)
			timeout?.let {
				timeout { socketTimeoutMillis = it.streamChunkTimeout.inWholeMilliseconds }
			}
		}.execute { response ->
			if (!response.status.isSuccess())
				error("LLM Stream Error: ${response.status}")
			
			
			val channel = response.bodyAsChannel()
			val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()
			var accumulatedContent: String? = null
			var accumulatedReasoning: String? = null
			var lastFinishReason: ChatResult.FinishReason? = null
			var lastUsage: Usage? = null
			var lastCreatedAt: Instant? = null
			var lastModel: String? = null
			
			while (!channel.isClosedForRead) {
				val line = channel.readLine() ?: break
				
				if (line.startsWith("data: ")) {
					val data = line.removePrefix("data: ").trim()
					
					if (data == "[DONE]") break
					
					if (data.isNotEmpty()) {
						val chunk = json.decodeFromString(chunkSerializer, data)
						
						val fragments = extractToolCalls(chunk)
						fragments?.forEach { fragment ->
							val pending = pendingToolCalls.getOrPut(fragment.index) { PendingToolCall() }
							fragment.id?.let { pending.id = it }
							fragment.name?.let { pending.name = it }
							fragment.arguments?.let { pending.arguments.append(it) }
						}
						
						val result = mapChunkToChatResult(chunk)
						val msg = result.message
						
						if (msg?.content != null)
							accumulatedContent = accumulatedContent.orEmpty() + msg.content
						
						if (msg?.reasoningContent != null)
							accumulatedReasoning = accumulatedReasoning.orEmpty() + msg.reasoningContent
						
						
						result.finishReason?.let { lastFinishReason = it }
						result.usage?.let { lastUsage = it }
						msg?.createdAt?.let { lastCreatedAt = it }
						msg?.model?.let { lastModel = it }
						
						emit(result.copy(toolCalls = fragments))
					}
				}
			}
			
			val toolCalls = buildToolCalls(pendingToolCalls)
			if (accumulatedContent != null || accumulatedReasoning != null || !toolCalls.isNullOrEmpty()) {
				emit(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage(
							content = accumulatedContent,
							reasoningContent = accumulatedReasoning,
							toolCalls = toolCalls,
							createdAt = lastCreatedAt ?: Clock.System.now(),
							model = lastModel,
						),
						finishReason = lastFinishReason,
						usage = lastUsage,
					)
				)
			}
		}
	}
	
	private suspend fun FlowCollector<ChatResult>.nonStreamChat(
		request: ChatRequest, apiKey: String, baseUrl: Url, timeout: ChatTimeout?
	) {
		val response = sharedHttpClient.post {
			configureRequest(request, apiKey, baseUrl, timeout)
		}
		
		if (!response.status.isSuccess()) {
			val errorBody = response.bodyAsText()
			emit(
				ChatResult.Assembled(
					message = ChatMessage.ErrorMessage(
						content = "LLM API Error (${response.status}): $errorBody",
						createdAt = Clock.System.now(),
						statusCode = response.status.value
					),
				)
			)
			return
		}
		
		val openAiResponse = response.body<Response>(responseTypeInfo)
		emit(mapToChatResult(openAiResponse))
	}
	
	
	private fun HttpRequestBuilder.configureRequest(
		request: ChatRequest, apiKey: String, baseUrl: Url, timeout: ChatTimeout?
	) {
		url("${baseUrl.value}/chat/completions")
		header(HttpHeaders.Authorization, "Bearer $apiKey")
		contentType(ContentType.Application.Json)
		setBody(createRequestBody(request), requestTypeInfo)
		timeout?.let {
			timeout {
				connectTimeoutMillis = it.connectTimeout.inWholeMilliseconds
				requestTimeoutMillis = it.requestTimeout.inWholeMilliseconds
			}
		}
	}
}
