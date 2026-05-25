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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.chat.AgentChat
import io.github.autotweaker.core.domain.agent.chat.AgentChatRequest
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import kotlin.time.Clock

class AgentChatTest {
	
	private val mockService: SettingService = mockk(relaxed = true)
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	
	private val testModelInfo = ModelInfo(
		modelId = "test-model",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		price = TokenPrice(
			inputPrice = listOf(PriceTier(0, null, testPrice)), outputPrice = listOf(PriceTier(0, null, testPrice))
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
	
	@AfterEach
	fun cleanup() {
		unmockkObject(ResilientChat)
	}
	
	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	@Test
	fun `collects assembled message with content and finish reason`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("hello world", Clock.System.now(), null, null),
			finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
		)
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
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
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chunkResult, model = UUID.randomUUID()))
			emit(CoreLlmResult(assembledResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("question")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val delta = results.filterIsInstance<AgentChatStreamResult.Delta>().first()
		assertEquals("let me think", delta.delta.reasoningContent)
		assertEquals("answer", delta.delta.content)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("let me think", assembled.message.reasoning)
		assertEquals("answer", assembled.message.content)
	}
	
	@Test
	fun `passes through deltas from multiple chunks`() = runTest {
		val now = Clock.System.now()
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(
				CoreLlmResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("hello ", now, null, null),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage("hello world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					UUID.randomUUID(),
				)
			)
		}
		
		val user = userMsg("greet")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val deltas = results.filterIsInstance<AgentChatStreamResult.Delta>()
		assertEquals(2, deltas.size)
		assertEquals("hello ", deltas[0].delta.content)
		assertEquals("world", deltas[1].delta.content)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
		assertNotNull(assembled.finishReason)
	}
	
	@Test
	fun `accumulates multiple errors and emits Failing`() = runTest {
		val now = Clock.System.now()
		val errorChatResult = ChatResult.Assembled(
			message = ChatMessage.ErrorMessage("service down", now, 503),
		)
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(errorChatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("help")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val failings = results.filterIsInstance<AgentChatStreamResult.Failing>()
		assertEquals(1, failings.size)
		assertEquals(1, failings[0].errors.size)
		assertEquals("service down", failings[0].errors[0].content)
		assertEquals(503, failings[0].errors[0].statusCode)
	}
	
	@Test
	fun `assembled message uses correct model info for usage snapshot`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("ok", now, null, null),
			usage = Usage(100, 50, 50),
		)
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("test")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals(Usage(100, 50, 50), assembled.message.usageSnapshot?.usage)
	}
	
	@Test
	fun `assembled message with reasoning content is included`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(null, now, "thinking...", null),
		)
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("question")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("thinking...", assembled.message.reasoning)
	}
	
	@Test
	fun `assembled message with tool calls creates pending tool calls`() = runTest {
		val now = Clock.System.now()
		val toolCalls = listOf(
			ChatMessage.AssistantMessage.ToolCall(
				id = "call1", name = "read_file",
				arguments = """{"file":"/tmp/test"}"""
			)
		)
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("done", now, null, toolCalls),
			finishReason = ChatResult.FinishReason("tool_calls", ChatResult.FinishReason.Type.TOOL),
		)
		
		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(
				CoreLlmResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage(null, now, null, null),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					chatResult,
					UUID.randomUUID(),
				)
			)
		}
		
		val user = userMsg("read test")
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		val request = AgentChatRequest(testModel, null, null, null, ctx)
		
		val results = AgentChat.execute(request, UUID.randomUUID(), mockService).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().last()
		assertEquals(1, assembled.toolCalls?.size)
		assertEquals("call1", assembled.toolCalls?.first()?.callId)
	}
}
