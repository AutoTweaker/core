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

class DeepSeekClientMappingTest {
	
	private val now = Clock.System.now()
	private val client = DeepSeekClient()
	
	// transform/usage 是 DeepSeekClient 的成员扩展，类外部不可见，通过反射调用编译后的实例方法
	private fun <T> invokeProtected(name: String, receiver: Any): T {
		val method = DeepSeekClient::class.java.declaredMethods
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
		val method = DeepSeekClient::class.java.declaredMethods
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
	
	private fun createRequestBody(request: ChatRequest): DeepSeekRequest =
		invokeSuspendProtected("transform", request)
	
	private fun mapToChatResult(response: DeepSeekResponse): ChatResult.Assembled =
		invokeProtected("transform", response)
	
	private fun mapChunkToChatResult(chunk: DeepSeekStreamChunk): ChatResult.Chunk =
		invokeProtected("transform", chunk)
	
	private fun chunkUsage(chunk: DeepSeekStreamChunk): Usage? =
		invokeProtected("usage", chunk)
	
	private fun request(
		model: String = "test",
		instructions: String? = null,
		messages: List<ChatMessage> = emptyList(),
		reasoning: ReasoningEffort? = null,
		stream: Boolean = false,
		maxTokens: Int? = null,
		tools: List<ChatRequest.Tool>? = null,
		temperature: Double? = null,
		jsonOutput: Boolean? = null,
	) = ChatRequest(
		model = model, instructions = instructions, messages = messages, reasoning = reasoning,
		stream = stream, maxTokens = maxTokens, tools = tools, temperature = temperature, jsonOutput = jsonOutput,
	)
	
	// region createRequestBody
	
	@Test
	fun `createRequestBody maps messages correctly`() {
		val userMsg = ChatMessage.User("hello".textPart(), now)
		val request = request(model = "deepseek-v4-pro", messages = listOf(userMsg))
		
		val body = createRequestBody(request)
		assertEquals("deepseek-v4-pro", body.model)
		assertEquals(1, body.messages.size)
		assertIs<DeepSeekMessage.UserMessage>(body.messages[0])
		assertEquals(listOf(DeepSeekMessage.UserMessage.Part.Text("hello")), body.messages[0].content)
	}
	
	@Test
	fun `createRequestBody maps SystemMessage`() {
		val request = request(
			instructions = "system prompt",
			messages = listOf(ChatMessage.User("hi".textPart(), now))
		)
		val body = createRequestBody(request)
		assertIs<DeepSeekMessage.SystemMessage>(body.messages[0])
		assertEquals("system prompt", (body.messages[0] as DeepSeekMessage.SystemMessage).content)
	}
	
	@Test
	fun `createRequestBody maps AssistantMessage with tool calls`() {
		val assistant = ChatMessage.Assistant(
			content = "using tool",
			timestamp = now,
			toolCalls = listOf(
				ChatMessage.Assistant.ToolCall("id1", "func1", "{}")
			)
		)
		val request = request(messages = listOf(assistant))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as DeepSeekMessage.AssistantMessage
		assertEquals("using tool", msg.content)
		assertEquals(1, msg.toolCalls?.size)
		assertEquals("id1", msg.toolCalls!![0].id)
		assertEquals("func1", msg.toolCalls[0].function.name)
	}
	
	@Test
	fun `createRequestBody maps ToolMessage`() {
		val tool = ChatMessage.ToolResult("result", now, "call-1")
		val request = request(messages = listOf(tool))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as DeepSeekMessage.ToolMessage
		assertEquals("result", msg.content)
		assertEquals("call-1", msg.toolCallId)
	}
	
	@Test
	fun `createRequestBody includes tools and thinking`() {
		val userMsg = ChatMessage.User("hi".textPart(), now)
		val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"key":"value"}""")
		val request = request(
			messages = listOf(userMsg),
			tools = listOf(ChatRequest.Tool("read_file", "read file", json)),
			reasoning = ReasoningEffort.HIGH,
			temperature = 0.7,
			maxTokens = 1000,
			stream = true,
			jsonOutput = true,
		)
		
		val body = createRequestBody(request)
		assertEquals(1, body.tools?.size)
		assertEquals("read_file", body.tools!![0].function.name)
		assertEquals(OpenAiThinking.Type.ENABLED, body.thinking?.type)
		assertEquals(DeepSeekRequest.Effort.HIGH, body.reasoningEffort)
		assertEquals(0.7, body.temperature)
		assertEquals(1000, body.maxTokens)
		assertNotNull(body.streamOptions)
		assertEquals(true, body.streamOptions.includeUsage)
		assertNotNull(body.responseFormat)
		assertNull(body.toolChoice)
	}
	
	// endregion
	
	// region mapToChatResult
	
	@Test
	fun `mapToChatResult maps response correctly`() {
		val response = DeepSeekResponse(
			created = now,
			choices = listOf(
				DeepSeekResponse.Choice(
					index = 0,
					message = DeepSeekMessage.AssistantMessage(
						content = "hello world",
						reasoningContent = "thinking...",
						toolCalls = listOf(
							OpenAiToolCall(
								id = "t1",
								function = OpenAiToolCall.Function("read", "{}")
							)
						)
					)
				)
			),
			usage = DeepSeekUsage(
				completionTokens = 50,
				promptTokens = 50,
				totalTokens = 100,
				promptCacheHitTokens = 10,
				promptCacheMissTokens = 40
			)
		)
		
		val result = mapToChatResult(response)
		assertIs<ChatMessage.Assistant>(result.message)
		assertEquals("hello world", result.message.content)
		assertEquals("thinking...", result.message.reasoningContent)
		assertEquals(1, result.message.toolCalls?.size)
		assertEquals(100, result.usage?.totalTokens)
		assertEquals(10, result.usage?.cacheHitTokens)
		assertEquals(40, result.usage?.cacheMissTokens)
	}
	
	@Test
	fun `mapToChatResult handles empty choices`() {
		val response = DeepSeekResponse(
			created = now,
			choices = emptyList(),
			usage = DeepSeekUsage(0, 0, 0)
		)
		val result = mapToChatResult(response)
		assertNull(result.message.content)
	}
	
	@Test
	fun `mapToChatResult includes reasoning tokens from details`() {
		val response = DeepSeekResponse(
			created = now,
			choices = listOf(
				DeepSeekResponse.Choice(
					index = 0,
					message = DeepSeekMessage.AssistantMessage(content = "ok")
				)
			),
			usage = DeepSeekUsage(
				totalTokens = 200, promptTokens = 100, completionTokens = 100,
				completionTokensDetails = DeepSeekUsage.CompletionTokensDetails(reasoningTokens = 30)
			)
		)
		assertEquals(30, mapToChatResult(response).usage?.reasoningTokens)
	}
	
	// endregion
	
	// region mapChunkToChatResult
	
	@Test
	fun `mapChunkToChatResult maps stream chunk`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
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
		assertNull(result.toolCalls)
	}
	
	@Test
	fun `chunk usage extension maps usage`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta()
				)
			),
			usage = DeepSeekUsage(
				completionTokens = 60,
				promptTokens = 40,
				totalTokens = 100,
				promptCacheHitTokens = 10,
				promptCacheMissTokens = 30
			)
		)
		val result = chunkUsage(chunk)
		assertEquals(100, result?.totalTokens)
		assertEquals(10, result?.cacheHitTokens)
		assertEquals(30, result?.cacheMissTokens)
	}
	
	@Test
	fun `chunk usage extension includes reasoning tokens`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(content = "ok")
				)
			),
			usage = DeepSeekUsage(
				totalTokens = 200, promptTokens = 100, completionTokens = 100,
				completionTokensDetails = DeepSeekUsage.CompletionTokensDetails(reasoningTokens = 40)
			)
		)
		assertEquals(40, chunkUsage(chunk)?.reasoningTokens)
	}
	
	// endregion
	
	// region extractToolCalls
	
	@Test
	fun `transform extracts tool calls from chunk`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(
						toolCalls = listOf(
							OpenAiChunkChoice.ChunkCall(
								index = 0, id = "call-1",
								function = OpenAiChunkChoice.ChunkCall.Function(
									name = "read_file", arguments = "{\"path\":\"/tmp\"}"
								)
							)
						)
					)
				)
			)
		)
		val toolCalls = mapChunkToChatResult(chunk).toolCalls
		assertNotNull(toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].id)
		assertEquals("read_file", toolCalls[0].name)
	}
	
	@Test
	fun `transform returns null tool calls when delta empty`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta()
				)
			)
		)
		assertNull(mapChunkToChatResult(chunk).toolCalls)
	}
	
	@Test
	fun `transform returns null tool calls for empty choices`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = emptyList()
		)
		assertNull(mapChunkToChatResult(chunk).toolCalls)
	}
	
	@Test
	fun `transform handles null function`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(
						toolCalls = listOf(
							OpenAiChunkChoice.ChunkCall(
								index = 0, id = null, function = null
							)
						)
					)
				)
			)
		)
		val toolCalls = mapChunkToChatResult(chunk).toolCalls
		assertNotNull(toolCalls)
		assertEquals(1, toolCalls.size)
		assertNull(toolCalls[0].id)
		assertNull(toolCalls[0].name)
	}
	
	// endregion
	
	// region boundary tests for branch coverage
	
	@Test
	fun `createRequestBody with thinking false`() {
		val userMsg = ChatMessage.User("hi".textPart(), now)
		val request = request(messages = listOf(userMsg), reasoning = ReasoningEffort(false))
		val body = createRequestBody(request)
		assertEquals(OpenAiThinking.Type.DISABLED, body.thinking?.type)
	}
	
	@Test
	fun `createRequestBody with thinking null`() {
		val userMsg = ChatMessage.User("hi".textPart(), now)
		val request = request(messages = listOf(userMsg), reasoning = null)
		val body = createRequestBody(request)
		assertNull(body.thinking)
	}
	
	@Test
	fun `createRequestBody with AssistantMessage without tool calls`() {
		val assistant = ChatMessage.Assistant(content = "reply", timestamp = now)
		val request = request(messages = listOf(assistant))
		val body = createRequestBody(request)
		val msg = body.messages[0] as DeepSeekMessage.AssistantMessage
		assertEquals("reply", msg.content)
		assertNull(msg.toolCalls)
	}
	
	@Test
	fun `mapChunkToChatResult with empty choices`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = emptyList()
		)
		val result = mapChunkToChatResult(chunk)
		assertNull(result.content)
	}
	
	@Test
	fun `mapChunkToChatResult with null delta content`() {
		val chunk = DeepSeekStreamChunk(
			created = now,
			choices = listOf(
				OpenAiChunkChoice(
					index = 0,
					delta = OpenAiChunkChoice.Delta(
						content = null, reasoningContent = null
					)
				)
			)
		)
		val result = mapChunkToChatResult(chunk)
		assertNull(result.content)
		assertNull(result.reasoningContent)
	}
	
	// endregion
}
