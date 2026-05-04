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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.AgentChatRequest
import io.github.autotweaker.core.agent.llm.AgentChatStreamResult
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.agentChat
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentStreamProcessorTest {
	
	private val mockModel: Model = mockk(relaxed = true)
	private val emittedOutputs = mutableListOf<AgentOutput>()
	private val statusChanges = mutableListOf<AgentStatus>()
	private var contextUpdateTransform: (suspend (AgentContext) -> AgentContext)? = null
	
	private fun createProcessor() {
		emittedOutputs.clear()
		statusChanges.clear()
		contextUpdateTransform = null
	}
	
	private fun newProcessor() = AgentStreamProcessor(
		emitOutput = { emittedOutputs.add(it) },
		onStatusChange = { statusChanges.add(it) },
		onContextUpdate = { transform -> contextUpdateTransform = transform },
	)
	
	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	private fun createRequest(): AgentChatRequest {
		val user = userMsg()
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		return AgentChatRequest(mockModel, null, null, null, ctx)
	}
	
	@After
	fun cleanup() {
		unmockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
	}
	
	// region success path
	
	@Test
	fun `process emits stream delta and context update for text response`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(AgentChatStreamResult.Delta(content = "hi", reasoningContent = null, toolCallFragments = null))
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						content = "hi", model = mockModel, timestamp = Clock.System.now(), usage = Usage(10, 5, 5)
					),
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		newProcessor().process(createRequest())
		
		assertTrue(emittedOutputs.any {
			it is AgentOutput.StreamDelta && it.delta.content == "hi"
		})
		assertTrue(emittedOutputs.any {
			it is AgentOutput.ContextUpdate && it.reason == AgentOutput.ContextUpdate.UpdateReason.LLM
		})
	}
	
	@Test
	fun `process emits stream delta with reasoning when reasoning content arrives`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Delta(
					content = null,
					reasoningContent = "let me think",
					toolCallFragments = null
				)
			)
			emit(
				AgentChatStreamResult.Delta(
					content = "answer",
					reasoningContent = "let me think",
					toolCallFragments = null
				)
			)
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						reasoning = "let me think",
						content = "answer",
						model = mockModel,
						timestamp = Clock.System.now()
					),
					toolCalls = null,
					finishReason = null,
				)
			)
		}
		
		newProcessor().process(createRequest())
		
		assertTrue(emittedOutputs.any {
			it is AgentOutput.StreamDelta && it.delta.reasoningContent == "let me think"
		})
	}
	
	@Test
	fun `process returns ToolCallsRequired when tool calls present`() = runTest {
		createProcessor()
		val toolCalls = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "c1",
				assistantMessageId = UUID.randomUUID(),
				name = "read_file",
				model = mockModel,
				arguments = """{"path":"/tmp"}""",
				timestamp = Clock.System.now()
			)
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						model = mockModel, timestamp = Clock.System.now()
					),
					toolCalls = toolCalls,
					finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
				)
			)
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.ToolCallsRequired>(result)
		assertEquals("c1", result.toolCalls[0].callId)
		assertTrue(emittedOutputs.any { it is AgentOutput.ToolCallRequest })
	}
	
	@Test
	fun `onContextUpdate transform sets assistant message on current round`() = runTest {
		createProcessor()
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(
			reasoning = "think",
			content = "answer",
			model = mockModel,
			timestamp = now,
			usage = Usage(10, 5, 5)
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = assistantMsg,
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		newProcessor().process(createRequest())
		
		assertNotNull(contextUpdateTransform)
		
		val user = userMsg("hello")
		val originalCtx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null))
		val updatedCtx = contextUpdateTransform!!(originalCtx)
		
		assertEquals("answer", updatedCtx.currentRound?.assistantMessage?.content)
		assertEquals("think", updatedCtx.currentRound?.assistantMessage?.reasoning)
		assertEquals(Usage(10, 5, 5), updatedCtx.currentRound?.assistantMessage?.usage)
	}
	
	@Test
	fun `onContextUpdate transform sets tool calls on current round`() = runTest {
		createProcessor()
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(model = mockModel, timestamp = now)
		val toolCalls = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "c1",
				assistantMessageId = UUID.randomUUID(),
				name = "read",
				model = mockModel,
				arguments = "{}",
				timestamp = now
			)
		)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = assistantMsg,
					toolCalls = toolCalls,
					finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
				)
			)
		}
		
		newProcessor().process(createRequest())
		
		assertNotNull(contextUpdateTransform)
		
		val user = userMsg("read")
		val originalCtx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null))
		val updatedCtx = contextUpdateTransform!!(originalCtx)
		
		assertEquals(1, updatedCtx.currentRound?.pendingToolCalls?.size)
		assertEquals("c1", updatedCtx.currentRound?.pendingToolCalls?.get(0)?.callId)
	}
	
	// endregion
	
	// region error path: Failing
	
	@Test
	fun `process emits Error when Failing without retrying`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Failing(
					errors = listOf(
						AgentChatStreamResult.Failing.Error(
							content = "service down",
							statusCode = HttpStatusCode.ServiceUnavailable,
							retrying = null,
							timestamp = Clock.System.now(),
						)
					)
				)
			)
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(emittedOutputs.any { it is AgentOutput.Error && it.type == AgentOutput.Error.Type.LLM })
	}
	
	@Test
	fun `Failing with null content uses default error message`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Failing(
					errors = listOf(
						AgentChatStreamResult.Failing.Error(
							content = null,
							statusCode = null,
							retrying = null,
							timestamp = Clock.System.now(),
						)
					)
				)
			)
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Failed>(result)
		assertEquals("All retries exhausted", result.message)
		val errorOutput = emittedOutputs.filterIsInstance<AgentOutput.Error>().first()
		assertEquals("All retries exhausted", errorOutput.message)
	}
	
	@Test
	fun `process changes status to RETRYING on failing with retrying model`() = runTest {
		createProcessor()
		val retryingModel: Model = mockk(relaxed = true)
		
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			emit(
				AgentChatStreamResult.Failing(
					errors = listOf(
						AgentChatStreamResult.Failing.Error(
							content = "error",
							statusCode = HttpStatusCode.ServiceUnavailable,
							retrying = retryingModel,
							timestamp = Clock.System.now(),
						)
					)
				)
			)
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						content = "ok", model = mockModel, timestamp = Clock.System.now()
					),
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		newProcessor().process(createRequest())
		
		assertTrue(statusChanges.contains(AgentStatus.RETRYING))
		assertTrue(emittedOutputs.any { it is AgentOutput.StreamError })
	}
	
	// endregion
	
	// region cancellation and exception
	
	@Test
	fun `process returns Cancelled on CancellationException`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			throw CancellationException("cancelled")
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Cancelled>(result)
	}
	
	@Test
	fun `process returns Failed on generic exception with message and cause`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			throw RuntimeException("outer", IllegalStateException("inner"))
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(result.message.contains("outer"))
		assertTrue(result.message.contains("IllegalStateException"))
		assertTrue(emittedOutputs.any { it is AgentOutput.Error })
	}
	
	@Test
	fun `exception with null message omits colon and message text`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			throw RuntimeException()
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(!result.message.contains(": "))
	}
	
	@Test
	fun `exception with null cause does not include cause section`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {
			throw RuntimeException("some error")
		}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(result.message.contains("some error"))
		assertTrue(!result.message.contains("caused by"))
	}
	
	// endregion
	
	// region no emission
	
	@Test
	fun `process returns Completed when flow emits nothing`() = runTest {
		createProcessor()
		mockkStatic("io.github.autotweaker.core.agent.llm.AgentChatKt")
		every { agentChat(any<AgentChatRequest>()) } returns flow {}
		
		val result = newProcessor().process(createRequest())
		
		assertIs<StreamProcessResult.Completed>(result)
	}
	
	// endregion
}
