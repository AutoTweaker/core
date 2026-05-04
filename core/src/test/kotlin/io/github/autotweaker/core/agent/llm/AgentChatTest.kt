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

import io.github.autotweaker.core.Price
import io.github.autotweaker.core.Provider.Model.*
import io.github.autotweaker.core.Provider.Model.TokenPrice.PriceTier
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentChatTest {
	
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	private val testModelInfo = ModelInfo(
		id = "test-id",
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
	private val testProvider = Provider("test-provider", testUrl, "sk-test", emptyList())
	private val testModel = Model("test-model", testProvider, testModelInfo, Config(0.7, 2048, null, null))
	
	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	@After
	fun cleanup() {
		unmockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
	}
	
	@Test
	fun `emits delta and assembled for successful response with content`() = runTest {
		val assistantMsg = ChatMessage.AssistantMessage(
			content = "hello world",
			createdAt = Clock.System.now(),
			reasoningContent = null,
			toolCalls = null,
		)
		val chatResult = ChatResult.Assembled(
			message = assistantMsg,
			finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
			usage = Usage(100, 50, 50),
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(ResilientChatResult(chatResult, null))
		}
		
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		assertTrue(results.any { it is AgentChatStreamResult.Assembled })
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
		assertNotNull(assembled.finishReason)
	}
	
	@Test
	fun `emits delta with reasoning when reasoning content arrives`() = runTest {
		val now = Clock.System.now()
		val chunkResult = ChatResult.Chunk(
			message = ChatMessage.AssistantMessage("answer", now, reasoningContent = "let me think"),
		)
		val assembledResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("answer", now, reasoningContent = "let me think"),
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(ResilientChatResult(chunkResult, null))
			emit(ResilientChatResult(assembledResult, null))
		}
		
		val user = userMsg("question")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		val delta = results.filterIsInstance<AgentChatStreamResult.Delta>().first()
		assertEquals("let me think", delta.reasoningContent)
		assertEquals("answer", delta.content)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("let me think", assembled.message.reasoning)
		assertEquals("answer", assembled.message.content)
	}
	
	@Test
	fun `passes through deltas from multiple chunks`() = runTest {
		val now = Clock.System.now()
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(
				ResilientChatResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("hello ", now, null, null),
					),
					null,
				)
			)
			emit(
				ResilientChatResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					null,
				)
			)
			emit(
				ResilientChatResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage("hello world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					null,
				)
			)
		}
		
		val user = userMsg("greet")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		val deltas = results.filterIsInstance<AgentChatStreamResult.Delta>()
		assertEquals(2, deltas.size)
		assertEquals("hello ", deltas[0].content)
		assertEquals("world", deltas[1].content)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
	}
	
	@Test
	fun `emits failing on error message`() = runTest {
		val errorMsg = ChatMessage.ErrorMessage(
			content = "service unavailable",
			createdAt = Clock.System.now(),
			statusCode = io.ktor.http.HttpStatusCode.ServiceUnavailable,
		)
		val errorChatResult = ChatResult.Assembled(message = errorMsg)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(ResilientChatResult(errorChatResult, null))
		}
		
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		val failings = results.filterIsInstance<AgentChatStreamResult.Failing>()
		assertEquals(1, failings.size)
		assertEquals("service unavailable", failings[0].errors.first().content)
	}
	
	@Test
	fun `handles all models exhausted silently`() = runTest {
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			throw IllegalStateException("All candidate models exhausted without success")
		}
		
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		assertTrue(results.none { it is AgentChatStreamResult.Assembled })
	}
	
	@Test
	fun `emits assembled with tool calls when assistant message has tool calls`() = runTest {
		val assistantToolCalls = listOf(
			ChatMessage.AssistantMessage.ToolCall("call-1", "read", """{"path":"/tmp"}""")
		)
		val assistantMsg = ChatMessage.AssistantMessage(
			content = null,
			createdAt = Clock.System.now(),
			reasoningContent = null,
			toolCalls = assistantToolCalls,
		)
		val chatResult = ChatResult.Assembled(message = assistantMsg)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(ResilientChatResult(chatResult, null))
		}
		
		val user = userMsg("read file")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		val toolCalls = assertNotNull(assembled.toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].callId)
		assertEquals("read", toolCalls[0].name)
	}
	
	@Test
	fun `uses retrying model as result model in Assembled`() = runTest {
		val fallbackModel = Model("fallback", testProvider, testModelInfo)
		val errorMsg = ChatMessage.ErrorMessage(
			content = "error",
			createdAt = Clock.System.now(),
			statusCode = io.ktor.http.HttpStatusCode.ServiceUnavailable,
		)
		val assistantMsg = ChatMessage.AssistantMessage(
			content = "recovered",
			createdAt = Clock.System.now(),
			reasoningContent = null,
			toolCalls = null,
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			emit(ResilientChatResult(ChatResult.Assembled(message = errorMsg), retrying = fallbackModel))
			emit(
				ResilientChatResult(
					ChatResult.Assembled(
						message = assistantMsg,
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					null,
				)
			)
		}
		
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		
		assertEquals("fallback", assembled.message.model.name)
	}
	
	@Test
	fun `passes tool call fragments through deltas`() = runTest {
		val toolCalls = listOf(
			ChatMessage.AssistantMessage.ToolCall("call-1", "read", """{"path":"/tmp"}""")
		)
		val now = Clock.System.now()
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
		every {
			resilientChat(any(), any(), any(), any())
		} returns flow {
			// chunk with tool call fragments
			emit(
				ResilientChatResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage(null, now, null, null),
						toolCalls = listOf(ChatResult.ChunkToolCall(0, "call-1", "read", """{"path":"/tmp"}""")),
					),
					null,
				)
			)
			// assembled with complete tool calls
			emit(
				ResilientChatResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage("done", now, null, toolCalls),
						finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
					),
					null,
				)
			)
		}
		
		val user = userMsg("read file")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = agentChat(request).toList()
		
		val delta = results.filterIsInstance<AgentChatStreamResult.Delta>().first()
		assertNotNull(delta.toolCallFragments)
		assertEquals(1, delta.toolCallFragments.size)
		assertEquals("call-1", delta.toolCallFragments[0].id)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		val tc = assertNotNull(assembled.toolCalls)
		assertEquals(1, tc.size)
		assertEquals("call-1", tc[0].callId)
	}
}
