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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

abstract class AbstractOpenAiClient<Request : Any, Response : Any, Chunk : Any>(
	private val requestTypeInfo: TypeInfo,
	private val responseTypeInfo: TypeInfo,
	private val chunkSerializer: KSerializer<Chunk>,
) : LlmClient, Loggable, Traceable {
	override suspend fun shutdown() = sharedHttpClient.close()
	
	private class PendingToolCall(
		var id: StringBuilder = StringBuilder(),
		var name: StringBuilder = StringBuilder(),
		val arguments: StringBuilder = StringBuilder()
	) {
		fun toToolCall() = ChatMessage.Assistant.ToolCall(id.toString(), name.toString(), arguments.toString())
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
	): Flow<ChatResult> = channelFlow {
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
			send(e.result)
		}.getOrElse { e ->
			log.error("Failed LLM request execution  provider={}  model={}", providerInfo.name, request.model, e)
			send(
				ChatResult.Failed(
					message = e.message(), statusCode = null, exception = e
				)
			)
		}
	}
	
	@OptIn(ExperimentalCoroutinesApi::class)
	private suspend fun ProducerScope<ChatResult>.streamChat(
		request: ChatRequest, apiKey: String, baseUrl: Url, timeout: ChatTimeout?
	) {
		coroutineScope {
			val chunkTimeout = timeout?.streamChunkTimeout
			val headersArrived = CompletableDeferred<Unit>()
			val responseJob = async(start = CoroutineStart.UNDISPATCHED) {
				sharedHttpClient.preparePost {
					configureRequest(request, apiKey, baseUrl, timeout)
				}.execute { response ->
					headersArrived.complete(Unit)
					collectStream(response, chunkTimeout)
				}
			}
			if (chunkTimeout != null) {
				select {
					headersArrived.onAwait { }
					responseJob.onAwait { }
					onTimeout(chunkTimeout) {
						responseJob.cancel()
						chunkTimeoutFailed(chunkTimeout, null)
					}
				}
			}
			responseJob.await()
		}
	}
	
	private suspend fun ProducerScope<ChatResult>.collectStream(
		response: HttpResponse, chunkTimeout: Duration?
	) {
		if (!response.status.isSuccess()) {
			val errorBody = response.bodyAsText()
			throw LlmFailedException(
				ChatResult.Failed(
					message = "LLM API Error (${response.status}): $errorBody",
					statusCode = response.status.value
				),
			)
		}
		
		var idleDeadline: Instant? = null
		if (chunkTimeout != null) {
			idleDeadline = Clock.System.now() + chunkTimeout
		}
		
		val channel = response.bodyAsChannel()
		try {
			val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()
			val content: StringBuilder = StringBuilder()
			val reasoning: StringBuilder = StringBuilder()
			var lastUsage: Usage? = null
			var lastTimestamp: Instant? = null
			
			while (!channel.isClosedForRead) {
				val line = readLineWithChunkTimeout(channel, chunkTimeout, idleDeadline) ?: break
				
				if (line.startsWith("data:")) {
					idleDeadline = chunkTimeout?.let { Clock.System.now() + it }
					val data = line.removePrefix("data:").trim()
					
					if (data == "[DONE]") break
					
					if (data.isNotEmpty()) {
						val chunk = json.decodeFromString(chunkSerializer, data)
						
						val result = chunk.transform()
						send(result)
						
						result.toolCalls?.forEach { fragment ->
							val pending = pendingToolCalls.getOrPut(fragment.index) { PendingToolCall() }
							fragment.id?.let { pending.id.append(it) }
							fragment.name?.let { pending.name.append(it) }
							fragment.arguments?.let { pending.arguments.append(it) }
						}
						
						result.content?.let {
							content.append(it)
						}
						
						result.reasoningContent?.let {
							reasoning.append(it)
						}
						
						chunk.usage()?.let { lastUsage = it }
						chunk.timestamp()?.let { lastTimestamp = it }
					}
				}
			}
			
			val toolCalls = if (pendingToolCalls.isEmpty()) null
			else pendingToolCalls.toSortedMap().values.map { it.toToolCall() }
			
			send(
				ChatResult.Assembled(
					message = ChatMessage.Assistant(
						content = content.toString(),
						reasoningContent = reasoning.toString(),
						toolCalls = toolCalls,
						timestamp = lastTimestamp.orNow(),
					),
					usage = lastUsage,
				)
			)
		} finally {
			trace.catching { channel.cancel() }
		}
	}
	
	private suspend fun ProducerScope<ChatResult>.nonStreamChat(
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
		
		send(response.body<Response>(responseTypeInfo).transform())
	}
	
	
	private suspend fun readLineWithChunkTimeout(
		channel: ByteReadChannel, timeout: Duration?, idleDeadline: Instant?
	): String? {
		if (timeout == null || idleDeadline == null) return channel.readLine()
		return trace.catching {
			val remaining = idleDeadline - Clock.System.now()
			withTimeout(remaining) { channel.readLine() }
		}.recoverException { e: TimeoutCancellationException ->
			chunkTimeoutFailed(timeout, e)
		}.getOrThrow()
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
	
	private fun chunkTimeoutFailed(timeout: Duration, cause: Throwable?): Nothing {
		throw LlmFailedException(
			ChatResult.Failed(
				message = "LLM stream chunk timeout after=${timeout.inWholeSeconds} seconds",
				statusCode = null,
				exception = cause
			)
		)
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
