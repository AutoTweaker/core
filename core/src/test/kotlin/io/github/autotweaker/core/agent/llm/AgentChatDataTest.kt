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

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class AgentChatDataTest {
	
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	private val testModelInfo = ModelInfo(
		modelId = "test-model-id",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		price = TokenPrice(
			inputPrice = listOf(PriceTier(0, null, testPrice)),
			outputPrice = listOf(PriceTier(0, null, testPrice))
		),
		supportsStreaming = true,
		supportsToolCalls = true,
		supportsReasoning = true,
		supportsImage = false,
		supportsJsonOutput = true,
	)
	private val testProvider = Provider(UUID.randomUUID(), "test-provider", testUrl, "sk-test", emptyList())
	private val testModel = Model(
		provider = testProvider,
		modelInfo = testModelInfo,
		config = Config(0.7, 2048, null, null),
		id = UUID.randomUUID()
	)
	
	@Test
	fun `construct request with all fields`() {
		val context = AgentContext(null, "system prompt", null, null, null)
		val tools = listOf(
			ChatRequest.Tool(
				"read",
				"read file",
				kotlinx.serialization.json.Json.parseToJsonElement("{}")
			)
		)
		val fallbackModels = listOf(testModel)
		val request = AgentChatRequest(
			model = testModel,
			fallbackModels = fallbackModels,
			thinking = true,
			tools = tools,
			context = context,
		)
		assertEquals(testModel, request.model)
		assertEquals(fallbackModels, request.fallbackModels)
		assertEquals(true, request.thinking)
		assertEquals(tools, request.tools)
		assertEquals(context, request.context)
	}
	
	@Test
	fun `construct request with null optionals`() {
		val context = AgentContext(null, null, null, null, null)
		val request = AgentChatRequest(testModel, null, null, null, context)
		assertNull(request.fallbackModels)
		assertNull(request.thinking)
		assertNull(request.tools)
	}
	
	@Test
	fun `failing result contains errors`() {
		val now = Clock.System.now()
		val errors = listOf(
			AgentChatStreamResult.Failing.Error(
				"error 1",
				500,
				UUID.fromString("00000000-0000-0000-0000-000000000000"),
				now
			),
			AgentChatStreamResult.Failing.Error("error 2", 503, testModel.id, now),
		)
		val failing = AgentChatStreamResult.Failing(errors)
		assertEquals(2, failing.errors.size)
		assertEquals("error 1", failing.errors[0].content)
		assertEquals(500, failing.errors[0].statusCode)
		assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000000"), failing.errors[0].model)
		assertEquals("error 2", failing.errors[1].content)
		assertEquals(testModel.id, failing.errors[1].model)
	}
	
	@Test
	fun `delta result with reasoning`() {
		val delta = AgentChatStreamResult.Delta(
			delta = StreamDelta(
				content = null,
				reasoningContent = "thinking step by step",
				toolCallFragments = null
			)
		)
		assertEquals("thinking step by step", delta.delta.reasoningContent)
		assertNull(delta.delta.content)
		assertNull(delta.delta.toolCallFragments)
	}
	
	@Test
	fun `delta result with content and reasoning`() {
		val delta = AgentChatStreamResult.Delta(
			delta = StreamDelta(
				content = "hello",
				reasoningContent = "think",
				toolCallFragments = null
			)
		)
		assertEquals("think", delta.delta.reasoningContent)
		assertEquals("hello", delta.delta.content)
	}
	
	@Test
	fun `delta result with content only`() {
		val delta = AgentChatStreamResult.Delta(
			delta = StreamDelta(
				content = "hello",
				reasoningContent = null,
				toolCallFragments = null
			)
		)
		assertNull(delta.delta.reasoningContent)
		assertEquals("hello", delta.delta.content)
	}
	
	@Test
	fun `delta result with tool call fragments`() {
		val fragments = listOf(ChatResult.ChunkToolCall(index = 0, id = "c1", name = "bash", arguments = "{}"))
		val delta = AgentChatStreamResult.Delta(
			delta = StreamDelta(
				content = null,
				reasoningContent = null,
				toolCallFragments = fragments
			)
		)
		assertEquals(1, delta.delta.toolCallFragments?.size)
		assertEquals("c1", delta.delta.toolCallFragments?.get(0)?.id)
	}
	
	@Test
	fun `assembled result with tool calls and finish reason`() {
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(
			reasoning = "think",
			content = "done",
			model = testModel,
			timestamp = now,
			usageSnapshot = UsageSnapshot(usage = Usage(50, 50), model = testModel.modelInfo),
		)
		val toolCalls = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "id1",
				assistantMessageId = UUID.randomUUID(),
				name = "read",
				model = testModel,
				arguments = "{}",
				timestamp = now
			)
		)
		val finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP)
		val assembled = AgentChatStreamResult.Assembled(assistantMsg, toolCalls, finishReason)
		
		assertEquals(assistantMsg, assembled.message)
		assertEquals(toolCalls, assembled.toolCalls)
		assertEquals(finishReason, assembled.finishReason)
		assertEquals(1, toolCalls.size)
	}
	
	@Test
	fun `assembled result without tool calls`() {
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(content = "done", model = testModel, timestamp = now)
		val assembled = AgentChatStreamResult.Assembled(assistantMsg, null, null)
		
		assertNull(assembled.toolCalls)
		assertNull(assembled.finishReason)
		assertEquals("done", assembled.message.content)
	}
}
