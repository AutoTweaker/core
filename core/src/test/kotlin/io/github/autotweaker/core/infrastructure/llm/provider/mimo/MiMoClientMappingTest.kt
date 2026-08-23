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

import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiChunkChoice
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiThinking
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiToolCall
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.test.*
import kotlin.time.Clock

class MiMoClientMappingTest {
	
	private val now = Clock.System.now()
	private val client = MiMoClient()
	
	// transform/usage 是 MiMoClient 的成员扩展，类外部不可见，通过反射调用编译后的实例方法
	private fun <T> invokeProtected(name: String, receiver: Any): T {
		val method = MiMoClient::class.java.declaredMethods
			.first {
				it.name == name && it.parameterTypes.size == 1 && !it.isBridge && it.parameterTypes[0].isInstance(
					receiver
				)
			}
		method.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		return try {
			method.invoke(client, receiver) as T
		} catch (e: java.lang.reflect.InvocationTargetException) {
			throw e.targetException
		}
	}
	
	// suspend 成员扩展编译后带 Continuation 参数；无挂起点时 invoke 直接返回结果，否则结果经 Continuation 传递
	private fun <T> invokeSuspendProtected(name: String, receiver: Any): T {
		val method = MiMoClient::class.java.declaredMethods
			.first {
				it.name == name && it.parameterTypes.size == 2 && !it.isBridge && it.parameterTypes[0].isInstance(
					receiver
				)
			}
		method.isAccessible = true
		var result: T? = null
		var failure: Throwable? = null
		val continuation = object : Continuation<T> {
			override val context: CoroutineContext = EmptyCoroutineContext
			override fun resumeWith(r: Result<T>) {
				r.onSuccess { result = it }.onFailure { failure = it }
			}
		}
		val returned = try {
			method.invoke(client, receiver, continuation)
		} catch (e: java.lang.reflect.InvocationTargetException) {
			throw e.targetException
		}
		@Suppress("UNCHECKED_CAST")
		if (returned !== COROUTINE_SUSPENDED) return returned as T
		failure?.let { throw it }
		return result as T
	}
	
	private fun chatRequest(
		model: String = "test",
		instructions: String? = null,
		messages: List<ChatMessage> = emptyList(),
		reasoning: ReasoningEffort? = null,
		stream: Boolean = false,
		maxTokens: Int? = null,
		tools: List<ChatRequest.Tool>? = null,
		temperature: Double? = null,
		jsonOutput: Boolean? = null,
	): ChatRequest = ChatRequest(
		model = model, instructions = instructions, messages = messages,
		reasoning = reasoning, stream = stream, maxTokens = maxTokens,
		tools = tools, temperature = temperature, jsonOutput = jsonOutput
	)
	
	private fun createRequestBody(request: ChatRequest): MiMoRequest =
		invokeSuspendProtected("transform", request)
	
	private fun mapToChatResult(response: MiMoResponse): ChatResult.Assembled =
		invokeProtected("transform", response)
	
	private fun mapChunkToChatResult(chunk: MiMoStreamChunk): ChatResult.Chunk =
		invokeProtected("transform", chunk)
	
	private fun extractToolCalls(chunk: MiMoStreamChunk): List<ChatResult.ChunkToolCall>? =
		invokeProtected<ChatResult.Chunk>("transform", chunk).toolCalls
	
	private fun chunkUsage(chunk: MiMoStreamChunk): Usage? =
		invokeProtected("usage", chunk)
	
	// region createRequestBody
	
	@Test
	fun `createRequestBody maps messages correctly`() {
		val userMsg = ChatMessage.User("hello".textPart(), now)
		val request = chatRequest(model = "mimo-v2-pro", messages = listOf(userMsg))
		
		val body = createRequestBody(request)
		assertEquals("mimo-v2-pro", body.model)
		assertEquals(1, body.messages.size)
		assertIs<MiMoMessage.UserMessage>(body.messages[0])
	}
	
	@Test
	fun `createRequestBody maps instructions to DeveloperMessage`() {
		val request = chatRequest(model = "test", instructions = "system prompt")
		val body = createRequestBody(request)
		assertIs<MiMoMessage.DeveloperMessage>(body.messages[0])
	}
	
	@Test
	fun `createRequestBody maps AssistantMessage with tool calls`() {
		val assistant = ChatMessage.Assistant(
			content = "using tool", timestamp = now,
			toolCalls = listOf(ChatMessage.Assistant.ToolCall("id1", "func1", "{}"))
		)
		val request = chatRequest(model = "test", messages = listOf(assistant))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as MiMoMessage.AssistantMessage
		assertEquals("using tool", msg.content)
		assertEquals(1, msg.toolCalls?.size)
		assertEquals("id1", msg.toolCalls!![0].id)
	}
	
	@Test
	fun `createRequestBody maps ToolMessage`() {
		val tool = ChatMessage.ToolResult("result", now, "call-1")
		val request = chatRequest(model = "test", messages = listOf(tool))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as MiMoMessage.ToolMessage
		assertEquals("result", msg.content)
		assertEquals("call-1", msg.toolCallId)
	}
	
	@Test
	fun `createRequestBody includes tools and thinking`() {
		val userMsg = ChatMessage.User("hi".textPart(), now)
		val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"key":"value"}""")
		val request = chatRequest(
			model = "test", messages = listOf(userMsg),
			tools = listOf(ChatRequest.Tool("read_file", "read file", json)),
			reasoning = ReasoningEffort(true), temperature = 0.7, maxTokens = 1000, jsonOutput = true
		)
		
		val body = createRequestBody(request)
		assertEquals(1, body.tools?.size)
		assertEquals("read_file", body.tools!![0].function.name)
		assertEquals(OpenAiThinking.Type.ENABLED, body.thinking?.type)
		assertEquals(0.7, body.temperature)
		assertEquals(1000, body.maxCompletionTokens)
		assertEquals("json_object", body.responseFormat?.type)
	}
	
	@Test
	fun `createRequestBody thinking false disabled`() {
		val userMsg = ChatMessage.User("hi".textPart(), now)
		val request = chatRequest(model = "test", messages = listOf(userMsg), reasoning = ReasoningEffort(false))
		val body = createRequestBody(request)
		assertEquals(OpenAiThinking.Type.DISABLED, body.thinking?.type)
	}
	
	// endregion
	
	// region mapToChatResult
	
	@Test
	fun `mapToChatResult maps response correctly`() {
		val response = MiMoResponse(
			id = "resp-1", created = now,
			choices = listOf(
				MiMoResponse.Choice(
					index = 0,
					message = MiMoMessage.AssistantMessage(
						content = "hello world", reasoningContent = "thinking...",
						toolCalls = listOf(
							OpenAiToolCall(
								id = "t1", function = OpenAiToolCall.Function("read", "{}")
							)
						)
					)
				)
			),
			usage = MiMoUsage(
				completionTokens = 50, promptTokens = 50, totalTokens = 100,
				completionTokensDetails = MiMoUsage.CompletionTokensDetails(reasoningTokens = 20),
				promptTokensDetails = MiMoUsage.PromptTokensDetails(cachedTokens = 10)
			)
		)
		
		val result = mapToChatResult(response)
		assertIs<ChatMessage.Assistant>(result.message)
		assertEquals("hello world", result.message.content)
		assertEquals("thinking...", result.message.reasoningContent)
		assertEquals(100, result.usage?.totalTokens)
		assertEquals(20, result.usage?.reasoningTokens)
		assertEquals(10, result.usage?.cacheHitTokens)
	}
	
	@Test
	fun `mapToChatResult handles empty choices`() {
		val response = MiMoResponse(
			id = "r1", created = now,
			choices = emptyList(),
			usage = MiMoUsage(0, 0, 0)
		)
		val result = mapToChatResult(response)
		assertNull(result.message.content)
	}
	
	@Test
	fun `mapToChatResult usage with null details`() {
		val response = MiMoResponse(
			id = "r1", created = now,
			choices = listOf(
				MiMoResponse.Choice(
					index = 0,
					message = MiMoMessage.AssistantMessage(content = "ok")
				)
			),
			usage = MiMoUsage(totalTokens = 50, promptTokens = 30, completionTokens = 20)
		)
		val result = mapToChatResult(response)
		assertEquals(50, result.usage?.totalTokens)
		assertNull(result.usage?.reasoningTokens)
		assertNull(result.usage?.cacheHitTokens)
	}
	
	// endregion
	
	// region mapChunkToChatResult
	
	@Test
	fun `mapChunkToChatResult maps stream chunk`() {
		val chunk = MiMoStreamChunk(
			id = "chunk-1", created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(
						content = "partial", reasoningContent = "thinking..."
					)
				)
			)
		)
		val result = mapChunkToChatResult(chunk)
		assertEquals("partial", result.content)
		assertEquals("thinking...", result.reasoningContent)
	}
	
	@Test
	fun `mapChunkToChatResult includes usage from chunk`() {
		val chunk = MiMoStreamChunk(
			id = "chunk-1", created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta()
				)
			),
			usage = MiMoUsage(
				completionTokens = 60, promptTokens = 40, totalTokens = 100,
				completionTokensDetails = MiMoUsage.CompletionTokensDetails(reasoningTokens = 20),
				promptTokensDetails = MiMoUsage.PromptTokensDetails(cachedTokens = 10)
			)
		)
		val usage = chunkUsage(chunk)
		assertEquals(100, usage?.totalTokens)
		assertEquals(20, usage?.reasoningTokens)
		assertEquals(10, usage?.cacheHitTokens)
	}
	
	// endregion
	
	// region extractToolCalls
	
	@Test
	fun `extractToolCalls extracts fragments from chunk`() {
		val chunk = MiMoStreamChunk(
			id = "c1", created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(
						toolCalls = listOf(
							OpenAiChunkChoice.ChunkCall(
								index = 0, id = "call-1",
								function = OpenAiChunkChoice.ChunkCall.Function(
									name = "read_file", arguments = "{}"
								)
							)
						)
					)
				)
			)
		)
		val fragments = extractToolCalls(chunk)
		assertNotNull(fragments)
		assertEquals(1, fragments.size)
	}
	
	@Test
	fun `extractToolCalls returns null for empty tool calls`() {
		val chunk = MiMoStreamChunk(
			id = "c1", created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta()
				)
			)
		)
		assertNull(extractToolCalls(chunk))
	}
	
	@Test
	fun `extractToolCalls returns null for empty choices`() {
		val chunk = MiMoStreamChunk(
			id = "c1", created = now,
			choices = emptyList()
		)
		assertNull(extractToolCalls(chunk))
	}
	
	// endregion
}
