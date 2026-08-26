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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.llm.LlmClient
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

abstract class AbstractOpenAiClient<Request : Any, Response : Any, Chunk : Any>(
	private val requestTypeInfo: TypeInfo,
	private val responseTypeInfo: TypeInfo,
	private val chunkSerializer: KSerializer<Chunk>,
) : LlmClient, Loggable, Traceable {
	override suspend fun shutdown() = sharedHttpClient.close()
	
	private class PendingToolCall(
		var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder()
	) {
		fun toToolCall() = ChatMessage.Assistant.ToolCall(id, name, arguments.toString())
	}
	
	protected abstract suspend fun ChatRequest.transform(): Request
	protected abstract fun Response.transform(): ChatResult
	protected abstract fun Chunk.transform(): ChatResult.Chunk
	protected abstract fun Chunk.usage(): Usage?
	protected abstract fun Chunk.timestamp(): Instant?
	
	override fun chat(
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
		}.recoverException { e: LlmFailedException ->
			emit(e.result)
		}.getOrElse { e ->
			log.error("Failed LLM request execution  provider={}  model={}", providerInfo.name, request.model, e)
			emit(
				ChatResult.Failed(
					message = e.message(), statusCode = null, exception = e
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
			if (!response.status.isSuccess()) {
				val errorBody = response.bodyAsText()
				throw LlmFailedException(
					ChatResult.Failed(
						message = "LLM API Error (${response.status}): $errorBody",
						statusCode = response.status.value
					),
				)
			}
			
			val channel = response.bodyAsChannel()
			val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()
			var content: String? = null
			var reasoning: String? = null
			var lastUsage: Usage? = null
			var lastTimestamp: Instant? = null
			
			while (!channel.isClosedForRead) {
				val line = channel.readLine() ?: break
				
				if (line.startsWith("data:")) {
					val data = line.removePrefix("data:").trim()
					
					if (data == "[DONE]") break
					
					if (data.isNotEmpty()) {
						val chunk = json.decodeFromString(chunkSerializer, data)
						
						val result = chunk.transform()
						emit(result)
						
						result.toolCalls?.forEach { fragment ->
							val pending = pendingToolCalls.getOrPut(fragment.index) { PendingToolCall() }
							fragment.id?.let { pending.id = it }
							fragment.name?.let { pending.name = it }
							fragment.arguments?.let { pending.arguments.append(it) }
						}
						
						result.content?.let {
							content = content.orEmpty() + it
						}
						
						result.reasoningContent?.let {
							reasoning = reasoning.orEmpty() + it
						}
						
						chunk.usage()?.let { lastUsage = it }
						chunk.timestamp()?.let { lastTimestamp = it }
					}
				}
			}
			
			val toolCalls = if (pendingToolCalls.isEmpty()) null
			else pendingToolCalls.toSortedMap().values.map { it.toToolCall() }
			
			emit(
				ChatResult.Assembled(
					message = ChatMessage.Assistant(
						content = content,
						reasoningContent = reasoning,
						toolCalls = toolCalls,
						timestamp = lastTimestamp ?: Clock.System.now(),
					),
					usage = lastUsage,
				)
			)
			
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
			throw LlmFailedException(
				ChatResult.Failed(
					message = "LLM API Error (${response.status}): $errorBody",
					statusCode = response.status.value
				),
			)
		}
		
		emit(response.body<Response>(responseTypeInfo).transform())
	}
	
	
	private suspend fun HttpRequestBuilder.configureRequest(
		request: ChatRequest, apiKey: String, baseUrl: Url, timeout: ChatTimeout?
	) {
		url("${baseUrl.value}/chat/completions")
		header(HttpHeaders.Authorization, "Bearer $apiKey")
		contentType(ContentType.Application.Json)
		setBody(request.transform(), requestTypeInfo)
		timeout?.let {
			timeout {
				connectTimeoutMillis = it.connectTimeout.inWholeMilliseconds
				requestTimeoutMillis = it.requestTimeout.inWholeMilliseconds
			}
		}
	}
	
	private class LlmFailedException(val result: ChatResult.Failed) : Exception()
	
	companion object {
		private val json = Json {
			ignoreUnknownKeys = true
			isLenient = true
			explicitNulls = false
			encodeDefaults = true
			coerceInputValues = true
		}
		
		private val sharedHttpClient = HttpClient {
			install(ContentNegotiation) {
				json(json)
			}
			install(HttpTimeout)
		}
	}
}
