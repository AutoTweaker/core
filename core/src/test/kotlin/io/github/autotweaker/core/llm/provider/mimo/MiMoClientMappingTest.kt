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

package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import kotlin.test.*
import kotlin.time.Clock

class MiMoClientMappingTest {
	
	private val now = Clock.System.now()
	private val client = MiMoClient()
	
	private fun <T> invokeProtected(name: String, vararg args: Any?): T {
		val method = MiMoClient::class.java.declaredMethods
			.first { it.name == name && it.parameterTypes.size == args.size }
		method.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		return method.invoke(client, *args) as T
	}
	
	private fun createRequestBody(request: ChatRequest): MiMoRequest =
		invokeProtected("createRequestBody", request)
	
	private fun mapToChatResult(response: MiMoResponse): ChatResult.Assembled =
		invokeProtected("mapToChatResult", response)
	
	private fun mapChunkToChatResult(chunk: MiMoStreamChunk): ChatResult.Chunk =
		invokeProtected("mapChunkToChatResult", chunk)
	
	private fun extractToolCalls(chunk: MiMoStreamChunk): List<*>? =
		invokeProtected("extractToolCalls", chunk)
	
	// region createRequestBody
	
	@Test
	fun `createRequestBody maps messages correctly`() {
		val userMsg = ChatMessage.UserMessage("hello", now)
		val request = ChatRequest(model = "mimo-v2-pro", messages = listOf(userMsg))
		
		val body = createRequestBody(request)
		assertEquals("mimo-v2-pro", body.model)
		assertEquals(1, body.messages.size)
		assertIs<MiMoMessage.UserMessage>(body.messages[0])
	}
	
	@Test
	fun `createRequestBody maps SystemMessage to DeveloperMessage`() {
		val sysMsg = ChatMessage.SystemMessage("system prompt", now)
		val request = ChatRequest(model = "test", messages = listOf(sysMsg))
		val body = createRequestBody(request)
		assertIs<MiMoMessage.DeveloperMessage>(body.messages[0])
	}
	
	@Test
	fun `createRequestBody maps AssistantMessage with tool calls`() {
		val assistant = ChatMessage.AssistantMessage(
			content = "using tool", createdAt = now,
			toolCalls = listOf(ChatMessage.AssistantMessage.ToolCall("id1", "func1", "{}"))
		)
		val request = ChatRequest(model = "test", messages = listOf(assistant))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as MiMoMessage.AssistantMessage
		assertEquals("using tool", (msg.content!![0] as MiMoMessage.Content.TextPart).text)
		assertEquals(1, msg.toolCalls?.size)
		assertEquals("id1", msg.toolCalls!![0].id)
	}
	
	@Test
	fun `createRequestBody maps ToolMessage`() {
		val tool = ChatMessage.ToolMessage("result", now, "call-1")
		val request = ChatRequest(model = "test", messages = listOf(tool))
		val body = createRequestBody(request)
		
		val msg = body.messages[0] as MiMoMessage.ToolMessage
		assertEquals("result", (msg.content[0] as MiMoMessage.Content.TextPart).text)
		assertEquals("call-1", msg.toolCallId)
	}
	
	@Test
	fun `createRequestBody filters ErrorMessage`() {
		val errorMsg = ChatMessage.ErrorMessage("error", now, null)
		val userMsg = ChatMessage.UserMessage("hi", now)
		val request = ChatRequest(model = "test", messages = listOf(errorMsg, userMsg))
		
		val body = createRequestBody(request)
		assertEquals(1, body.messages.size)
		assertIs<MiMoMessage.UserMessage>(body.messages[0])
	}
	
	@Test
	fun `createRequestBody includes tools and thinking`() {
		val userMsg = ChatMessage.UserMessage("hi", now)
		val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"key":"value"}""")
		val request = ChatRequest(
			model = "test", messages = listOf(userMsg),
			tools = listOf(ChatRequest.Tool("read_file", "read file", json)),
			thinking = true, temperature = 0.7, maxTokens = 1000,
			topP = 0.9, frequencyPenalty = 0.5, presencePenalty = 0.3,
			toolCallRequired = true,
			responseFormat = ChatRequest.ResponseFormat(ChatRequest.ResponseFormat.Type.JSON_OBJECT)
		)
		
		val body = createRequestBody(request)
		assertEquals(1, body.tools?.size)
		assertEquals("read_file", body.tools!![0].function.name)
		assertEquals(OpenAiRequest.Thinking.Type.ENABLED, body.thinking?.type)
		assertEquals(0.7, body.temperature)
		assertEquals(1000, body.maxCompletionTokens)
		assertEquals(0.9, body.topP)
		assertEquals(0.5, body.frequencyPenalty)
		assertEquals(0.3, body.presencePenalty)
	}
	
	@Test
	fun `createRequestBody thinking false disabled`() {
		val userMsg = ChatMessage.UserMessage("hi", now)
		val request = ChatRequest(model = "test", messages = listOf(userMsg), thinking = false)
		val body = createRequestBody(request)
		assertEquals(OpenAiRequest.Thinking.Type.DISABLED, body.thinking?.type)
	}
	
	// endregion
	
	// region mapToChatResult
	
	@Test
	fun `mapToChatResult maps response correctly`() {
		val response = MiMoResponse(
			id = "resp-1", created = now, model = "mimo-v2-pro",
			choices = listOf(
				MiMoResponse.Choice(
					index = 0,
					message = MiMoResponse.Choice.Message(
						content = "hello world", reasoningContent = "thinking...",
						toolCalls = listOf(
							MiMoToolCall(
								id = "t1", function = MiMoToolCall.Function("read", "{}")
							)
						)
					),
					finishReason = MiMoFinishReason.STOP
				)
			),
			usage = MiMoUsage(
				completionTokens = 50, promptTokens = 50, totalTokens = 100,
				completionTokensDetails = MiMoUsage.CompletionTokensDetails(reasoningTokens = 20),
				promptTokensDetails = MiMoUsage.PromptTokensDetails(cachedTokens = 10)
			)
		)
		
		val result = mapToChatResult(response)
		assertIs<ChatMessage.AssistantMessage>(result.message)
		assertEquals("hello world", result.message.content)
		assertEquals("thinking...", result.message.reasoningContent)
		assertEquals("stop", result.finishReason?.reason)
		assertEquals(ChatResult.FinishReason.Type.STOP, result.finishReason?.type)
		assertEquals(100, result.usage?.totalTokens)
		assertEquals(20, result.usage?.reasoningTokens)
		assertEquals(10, result.usage?.cacheHitTokens)
	}
	
	@Test
	fun `mapToChatResult handles empty choices`() {
		val response = MiMoResponse(
			id = "r1", created = now, model = "m",
			choices = emptyList(),
			usage = MiMoUsage(0, 0, 0)
		)
		val result = mapToChatResult(response)
		assertNull(result.message.content)
	}
	
	@Test
	fun `mapToChatResult all finish reasons`() {
		for ((reason, expectedType) in listOf(
			MiMoFinishReason.TOOL_CALLS to ChatResult.FinishReason.Type.TOOL,
			MiMoFinishReason.CONTENT_FILTER to ChatResult.FinishReason.Type.FILTER,
			MiMoFinishReason.LENGTH to ChatResult.FinishReason.Type.LENGTH,
			MiMoFinishReason.REPETITION_TRUNCATION to ChatResult.FinishReason.Type.ERROR
		)) {
			val response = MiMoResponse(
				id = "r1", created = now, model = "m",
				choices = listOf(
					MiMoResponse.Choice(
						index = 0,
						message = MiMoResponse.Choice.Message(content = "x"),
						finishReason = reason
					)
				),
				usage = MiMoUsage(0, 0, 0)
			)
			assertEquals(expectedType, mapToChatResult(response).finishReason?.type)
		}
	}
	
	@Test
	fun `mapToChatResult usage with null details`() {
		val response = MiMoResponse(
			id = "r1", created = now, model = "m",
			choices = listOf(
				MiMoResponse.Choice(
					index = 0,
					message = MiMoResponse.Choice.Message(content = "ok"),
					finishReason = MiMoFinishReason.STOP
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
			id = "chunk-1", created = now, model = "mimo",
			choices = listOf(
				MiMoStreamChunk.Choice(
					index = 0,
					delta = MiMoStreamChunk.Choice.Delta(
						content = "partial", reasoningContent = "thinking..."
					),
					finishReason = null
				)
			)
		)
		val result = mapChunkToChatResult(chunk)
		assertEquals("partial", result.message?.content)
		assertEquals("thinking...", (result.message as ChatMessage.AssistantMessage).reasoningContent)
		assertNull(result.finishReason)
	}
	
	@Test
	fun `mapChunkToChatResult includes usage from chunk`() {
		val chunk = MiMoStreamChunk(
			id = "chunk-1", created = now, model = "mimo",
			choices = listOf(
				MiMoStreamChunk.Choice(
					index = 0,
					delta = MiMoStreamChunk.Choice.Delta(),
					finishReason = MiMoFinishReason.STOP
				)
			),
			usage = MiMoUsage(
				completionTokens = 60, promptTokens = 40, totalTokens = 100,
				completionTokensDetails = MiMoUsage.CompletionTokensDetails(reasoningTokens = 20),
				promptTokensDetails = MiMoUsage.PromptTokensDetails(cachedTokens = 10)
			)
		)
		val result = mapChunkToChatResult(chunk)
		assertEquals(100, result.usage?.totalTokens)
		assertEquals(20, result.usage?.reasoningTokens)
		assertEquals(10, result.usage?.cacheHitTokens)
	}
	
	// endregion
	
	// region extractToolCalls
	
	@Test
	fun `extractToolCalls extracts fragments from chunk`() {
		val chunk = MiMoStreamChunk(
			id = "c1", created = now, model = "m",
			choices = listOf(
				MiMoStreamChunk.Choice(
					index = 0,
					delta = MiMoStreamChunk.Choice.Delta(
						toolCalls = listOf(
							MiMoStreamChunk.Choice.ToolCall(
								index = 0, id = "call-1",
								function = MiMoStreamChunk.Choice.ToolCall.Function(
									name = "read_file", arguments = "{}"
								)
							)
						)
					),
					finishReason = null
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
			id = "c1", created = now, model = "m",
			choices = listOf(
				MiMoStreamChunk.Choice(
					index = 0,
					delta = MiMoStreamChunk.Choice.Delta(),
					finishReason = null
				)
			)
		)
		assertNull(extractToolCalls(chunk))
	}
	
	@Test
	fun `extractToolCalls returns null for empty choices`() {
		val chunk = MiMoStreamChunk(
			id = "c1", created = now, model = "m",
			choices = emptyList()
		)
		assertNull(extractToolCalls(chunk))
	}
	
	// endregion
}
