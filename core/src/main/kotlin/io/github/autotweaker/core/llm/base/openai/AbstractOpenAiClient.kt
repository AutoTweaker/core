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

package io.github.autotweaker.core.llm.base.openai

import io.github.autotweaker.core.Url
import io.github.autotweaker.core.llm.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

abstract class AbstractOpenAiClient<
		Request : OpenAiRequest,
		Response : OpenAiResponse,
		Chunk : OpenAiStreamChunk>(
	private val requestTypeInfo: TypeInfo,
	private val responseTypeInfo: TypeInfo,
	private val chunkSerializer: KSerializer<Chunk>,
) : LlmClient {
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
		}
		
		private fun buildToolCalls(
			pendingToolCalls: Map<Int, PendingToolCall>
		): List<ChatMessage.AssistantMessage.ToolCall>? {
			if (pendingToolCalls.isEmpty()) return null
			return pendingToolCalls.toSortedMap().values.map { it.toToolCall() }
		}
	}
	
	class PendingToolCall(
		var id: String = "",
		var name: String = "",
		val arguments: StringBuilder = StringBuilder()
	) {
		fun toToolCall() = ChatMessage.AssistantMessage.ToolCall(id, name, arguments.toString())
	}
	
	protected abstract fun createRequestBody(request: ChatRequest): Request
	protected abstract fun mapToChatResult(response: Response): ChatResult
	protected abstract fun mapChunkToChatResult(chunk: Chunk): ChatResult
	protected abstract fun extractToolCalls(chunk: Chunk): List<ChatResult.ChunkToolCall>?
	
	override suspend fun chat(request: ChatRequest, apiKey: String, baseUrl: Url?): Flow<ChatResult> = flow {
		val effectiveBaseUrl = baseUrl ?: providerInfo.baseUrl
		try {
			if (request.stream) {
				val body = createRequestBody(request)
				
				sharedHttpClient.preparePost("${effectiveBaseUrl.value}/chat/completions") {
					header(HttpHeaders.Authorization, "Bearer $apiKey")
					contentType(ContentType.Application.Json)
					setBody(body, requestTypeInfo)
				}.execute { response ->
					if (!response.status.isSuccess()) {
						throw IllegalStateException("LLM Stream Error: ${response.status}")
					}
					
					val channel = response.bodyAsChannel()
					val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()
					var accumulatedContent: String? = null
					var accumulatedReasoning: String? = null
					var lastFinishReason: ChatResult.FinishReason? = null
					var lastUsage: Usage? = null
					var lastCreatedAt: kotlin.time.Instant? = null
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
									val pending =
										pendingToolCalls.getOrPut(fragment.index) { PendingToolCall() }
									if (fragment.id != null) pending.id = fragment.id
									if (fragment.name != null) pending.name = fragment.name
									if (fragment.arguments != null) pending.arguments.append(fragment.arguments)
								}
								
								val result = mapChunkToChatResult(chunk) as ChatResult.Chunk
								val msg = result.message
								
								if (msg?.content != null) {
									accumulatedContent = (accumulatedContent ?: "") + msg.content
								}
								if (msg?.reasoningContent != null) {
									accumulatedReasoning = (accumulatedReasoning ?: "") + msg.reasoningContent
								}
								
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
			} else {
				val response = sharedHttpClient.post("${effectiveBaseUrl.value}/chat/completions") {
					header(HttpHeaders.Authorization, "Bearer $apiKey")
					contentType(ContentType.Application.Json)
					setBody(createRequestBody(request), requestTypeInfo)
				}
				
				if (!response.status.isSuccess()) {
					val errorBody = response.bodyAsText()
					emit(
						ChatResult.Assembled(
							message = ChatMessage.ErrorMessage(
								content = "LLM API Error (${response.status}): $errorBody",
								createdAt = Clock.System.now(),
								statusCode = response.status
							),
						)
					)
					return@flow
				}
				
				val openAiResponse = response.body<Response>(responseTypeInfo)
				emit(mapToChatResult(openAiResponse))
			}
		} catch (e: Throwable) {
			emit(
				ChatResult.Assembled(
					message = ChatMessage.ErrorMessage(
						content = e.message ?: "Unknown error",
						createdAt = Clock.System.now(),
						statusCode = null
					),
				)
			)
		}
	}
}
