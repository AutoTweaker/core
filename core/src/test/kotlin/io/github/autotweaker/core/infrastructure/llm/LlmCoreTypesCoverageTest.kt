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

package io.github.autotweaker.core.infrastructure.llm

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.llm.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class LlmCoreTypesCoverageTest {
	private val now = Clock.System.now()
	
	@Test
	fun `ChatMessage sealed subtypes coverage`() {
		val messages = listOf<ChatMessage>(
			ChatMessage.User(listOf(ContentPart.Text("hi")), now),
			ChatMessage.Assistant("reply", now),
			ChatMessage.ToolResult("result", now, "call-1")
		)
		assertEquals(
			listOf("user", "assistant", "tool"),
			messages.map { msg ->
				when (msg) {
					is ChatMessage.User -> "user"
					is ChatMessage.Assistant -> "assistant"
					is ChatMessage.ToolResult -> "tool"
				}
			}
		)
	}
	
	@Test
	fun `ChatMessage User with image part`() {
		val pic = Sha256(ByteArray(32) { it.toByte() })
		val msg = ChatMessage.User(listOf(ContentPart.Text("hi"), ContentPart.Image("image/jpeg", pic)), now)
		assertEquals(2, msg.content.size)
		val image = msg.content[1] as ContentPart.Image
		assertEquals("image/jpeg", image.mimeType)
		assertContentEquals(pic.bytes, image.data.bytes)
	}
	
	@Test
	fun `ChatMessage User with text part`() {
		val msg = ChatMessage.User(listOf(ContentPart.Text("hi")), now)
		assertEquals(1, msg.content.size)
		assertEquals(ContentPart.Text("hi"), msg.content[0])
	}
	
	@Test
	fun `ChatMessage Assistant all fields`() {
		val tc = ChatMessage.Assistant.ToolCall("id1", "read", "{}")
		val msg = ChatMessage.Assistant(
			content = "reply",
			timestamp = now,
			reasoningContent = "thinking",
			toolCalls = listOf(tc)
		)
		assertEquals("reply", msg.content)
		assertEquals("thinking", msg.reasoningContent)
		assertEquals(1, msg.toolCalls?.size)
		assertEquals("id1", msg.toolCalls!![0].id)
		assertEquals("read", msg.toolCalls!![0].name)
		assertEquals("{}", msg.toolCalls!![0].arguments)
	}
	
	@Test
	fun `ChatMessage Assistant minimal fields`() {
		val msg = ChatMessage.Assistant(content = null, timestamp = now)
		assertNull(msg.content)
		assertNull(msg.reasoningContent)
		assertNull(msg.toolCalls)
	}
	
	@Test
	fun `ChatMessage ToolResult all fields`() {
		val msg = ChatMessage.ToolResult("result", now, "call-1")
		assertEquals("result", msg.content)
		assertEquals("call-1", msg.toolCallId)
	}
	
	@Test
	fun `ChatResult Failed with status`() {
		val result = ChatResult.Failed("error", 500)
		assertEquals("error", result.message)
		assertEquals(500, result.statusCode)
		assertNull(result.exception)
	}
	
	@Test
	fun `ChatResult Failed without status`() {
		val result = ChatResult.Failed("error", null)
		assertEquals("error", result.message)
		assertNull(result.statusCode)
	}
	
	@Test
	fun `ChatRequest all fields`() {
		val params = buildJsonObject { put("key", JsonPrimitive("value")) }
		val req = ChatRequest(
			model = "test-model",
			instructions = "be helpful",
			messages = listOf(ChatMessage.User(listOf(ContentPart.Text("hi")), now)),
			reasoning = ReasoningEffort.MEDIUM,
			stream = true,
			maxTokens = 500,
			tools = listOf(ChatRequest.Tool("read", "desc", params)),
			temperature = 0.5,
			jsonOutput = true
		)
		assertEquals("test-model", req.model)
		assertEquals("be helpful", req.instructions)
		assertEquals(ReasoningEffort.MEDIUM, req.reasoning)
		assertEquals(true, req.stream)
		assertEquals(500, req.maxTokens)
		assertEquals(1, req.tools?.size)
		assertEquals("read", req.tools!![0].name)
		assertEquals(0.5, req.temperature)
		assertEquals(true, req.jsonOutput)
	}
	
	@Test
	fun `ChatRequest minimal fields`() {
		val req = ChatRequest(
			model = "m",
			instructions = null,
			messages = listOf(ChatMessage.User(listOf(ContentPart.Text("hi")), now)),
			reasoning = null,
			stream = false,
			maxTokens = null,
			tools = null,
			temperature = null,
			jsonOutput = null
		)
		assertEquals("m", req.model)
		assertNull(req.instructions)
		assertNull(req.reasoning)
		assertEquals(false, req.stream)
		assertNull(req.maxTokens)
		assertNull(req.tools)
		assertNull(req.temperature)
		assertNull(req.jsonOutput)
	}
	
	@Test
	fun `ChatResult ChunkToolCall all fields`() {
		val call = ChatResult.ChunkToolCall(index = 0, id = "id1", name = "read", arguments = "{}")
		assertEquals(0, call.index)
		assertEquals("id1", call.id)
		assertEquals("read", call.name)
		assertEquals("{}", call.arguments)
	}
	
	@Test
	fun `ChatResult Assembled all fields`() {
		val result = ChatResult.Assembled(
			message = ChatMessage.Assistant("ok", now),
			usage = Usage(promptTokens = 40, completionTokens = 60, reasoningTokens = 10, cacheHitTokens = 5)
		)
		assertEquals("ok", result.message.content)
		assertEquals(100, result.usage?.totalTokens)
		assertEquals(10, result.usage?.reasoningTokens)
		assertEquals(5, result.usage?.cacheHitTokens)
	}
	
	@Test
	fun `ChatResult Chunk minimal fields`() {
		val result = ChatResult.Chunk(content = null, reasoningContent = null, toolCalls = null)
		assertNull(result.content)
		assertNull(result.reasoningContent)
		assertNull(result.toolCalls)
	}
	
	@Test
	fun `Usage all fields`() {
		val usage = Usage(
			promptTokens = 40,
			completionTokens = 60,
			reasoningTokens = 10,
			cacheHitTokens = 20
		)
		assertEquals(100, usage.totalTokens)
		assertEquals(40, usage.promptTokens)
		assertEquals(60, usage.completionTokens)
		assertEquals(10, usage.reasoningTokens)
		assertEquals(20, usage.cacheHitTokens)
		assertEquals(20, usage.cacheMissTokens)
	}
	
	@Test
	fun `Usage minimal fields`() {
		val usage = Usage(5, 5)
		assertEquals(10, usage.totalTokens)
		assertNull(usage.reasoningTokens)
		assertNull(usage.cacheHitTokens)
	}
	
	@Test
	fun `LlmClientLoader load deepseek`() {
		val client = LlmClientLoader.load("deepseek")
		assertEquals("deepseek", client.providerInfo.name)
	}
	
	@Test
	fun `LlmClientLoader load mimo`() {
		val client = LlmClientLoader.load("mimo")
		assertEquals("mimo", client.providerInfo.name)
	}
	
	@Test
	fun `LlmClientLoader load invalid provider`() {
		val result = runCatching { LlmClientLoader.load("nonexistent") }
		assert(result.isFailure)
	}
	
	@Test
	fun `LlmClientLoader availableProviders returns registered providers`() {
		val providers = LlmClientLoader.available()
		assert(providers.contains("deepseek"))
		assert(providers.contains("mimo"))
		assert(providers.size >= 2)
	}
}
