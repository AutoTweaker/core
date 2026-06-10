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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.chat.AgentStreamProcessor.StreamProcessResult
import io.github.autotweaker.core.domain.model.Model
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentStreamProcessorTest {
	private val mockModel: Model = mockk(relaxed = true)
	private val emittedOutputs = mutableListOf<AgentOutput>()
	
	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	private fun createRequest(): AgentChatRequest {
		val user = userMsg()
		val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
		return AgentChatRequest(mockModel, null, null, null, ctx)
	}
	
	@AfterTest
	fun cleanup() {
		unmockkObject(AgentChat)
	}
	
	// region success path
	
	@Test
	fun `process emits stream delta and context update for text response`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Delta(
					delta = StreamDelta(
						content = "hi",
						reasoningContent = null,
						toolCallFragments = null
					)
				)
			)
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						content = "hi",
						modelId = mockModel.id,
						timestamp = Clock.System.now(),
						usageSnapshot = UsageSnapshot(usage = Usage(5, 5), model = mockModel.modelInfo)
					),
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { emittedOutputs.add(it) }
		)
		
		assertTrue(emittedOutputs.any {
			it is AgentOutput.LlmDelta && it.delta.content == "hi"
		})
	}
	
	@Test
	fun `process emits stream delta with reasoning when reasoning content arrives`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Delta(
					delta = StreamDelta(
						content = null,
						reasoningContent = "let me think",
						toolCallFragments = null
					)
				)
			)
			emit(
				AgentChatStreamResult.Delta(
					delta = StreamDelta(
						content = "answer",
						reasoningContent = "let me think",
						toolCallFragments = null
					)
				)
			)
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						reasoning = "let me think",
						content = "answer",
						modelId = mockModel.id,
						timestamp = Clock.System.now()
					),
					toolCalls = null,
					finishReason = null,
				)
			)
		}
		
		AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { emittedOutputs.add(it) }
		)
		
		assertTrue(emittedOutputs.any {
			it is AgentOutput.LlmDelta && it.delta.reasoningContent == "let me think"
		})
	}
	
	@Test
	fun `process returns ToolCallsRequired when tool calls present`() = runTest {
		val toolCalls = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "c1",
				assistantMessageId = UUID.randomUUID(),
				name = "read_file",
				modelId = mockModel.id,
				arguments = """{"path":"/tmp"}""",
				timestamp = Clock.System.now(), validatedArgs = null
			)
		)
		
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						modelId = mockModel.id, timestamp = Clock.System.now()
					),
					toolCalls = toolCalls,
					finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
				)
			)
		}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { }
		)
		
		assertIs<StreamProcessResult.ToolCallsRequired>(result)
		assertEquals("c1", result.toolCalls[0].callId)
	}
	
	@Test
	fun `onContextUpdate transform sets assistant message on current round`() = runTest {
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(
			reasoning = "think",
			content = "answer",
			modelId = mockModel.id,
			timestamp = now,
			usageSnapshot = UsageSnapshot(usage = Usage(5, 5), model = mockModel.modelInfo)
		)
		
		var capturedTransform: (suspend (AgentContext) -> AgentContext)? = null
		
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = assistantMsg,
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { capturedTransform = it },
			onOutput = { }
		)
		
		val user = userMsg("hello")
		val originalCtx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null))
		val updatedCtx = capturedTransform!!(originalCtx)
		
		assertEquals("answer", updatedCtx.currentRound?.assistantMessage?.content)
		assertEquals("think", updatedCtx.currentRound?.assistantMessage?.reasoning)
		assertEquals(Usage(5, 5), updatedCtx.currentRound?.assistantMessage?.usageSnapshot?.usage)
	}
	
	@Test
	fun `onContextUpdate transform sets tool calls on current round`() = runTest {
		val now = Clock.System.now()
		val assistantMsg = AgentContext.Message.Assistant(modelId = mockModel.id, timestamp = now)
		val toolCalls = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "c1",
				assistantMessageId = UUID.randomUUID(),
				name = "read",
				modelId = mockModel.id,
				arguments = "{}",
				timestamp = now, validatedArgs = null
			)
		)
		
		var capturedTransform: (suspend (AgentContext) -> AgentContext)? = null
		
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Assembled(
					message = assistantMsg,
					toolCalls = toolCalls,
					finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
				)
			)
		}
		
		AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { capturedTransform = it },
			onOutput = { }
		)
		
		val user = userMsg("read")
		val originalCtx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null))
		val updatedCtx = capturedTransform!!(originalCtx)
		
		assertEquals(1, updatedCtx.currentRound?.pendingToolCalls?.size)
		assertEquals("c1", updatedCtx.currentRound?.pendingToolCalls?.get(0)?.callId)
	}
	
	// endregion
	
	// region error path: Failing + success after retry
	
	@Test
	fun `process emits LlmError on failing and continues`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			emit(
				AgentChatStreamResult.Failing(
					errors = listOf(
						AgentChatStreamResult.Failing.Error(
							content = "error",
							statusCode = 503,
							model = UUID.randomUUID(),
							timestamp = Clock.System.now(),
						)
					)
				)
			)
			emit(
				AgentChatStreamResult.Assembled(
					message = AgentContext.Message.Assistant(
						content = "ok", modelId = mockModel.id, timestamp = Clock.System.now()
					),
					toolCalls = null,
					finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
				)
			)
		}
		
		AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { emittedOutputs.add(it) }
		)
		
		assertTrue(emittedOutputs.any { it is AgentOutput.LlmError })
	}
	
	// endregion
	
	// region cancellation and exception
	
	@Test
	fun `process returns Cancelled on CancellationException`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			throw CancellationException("cancelled")
		}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { }
		)
		
		assertIs<StreamProcessResult.Cancelled>(result)
	}
	
	@Test
	fun `process returns Failed on generic exception with message and cause`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			throw RuntimeException("outer", IllegalStateException("inner"))
		}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { emittedOutputs.add(it) }
		)
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(result.message.contains("outer"))
		assertTrue(result.message.contains("IllegalStateException"))
		assertTrue(emittedOutputs.any { it is AgentOutput.Error })
	}
	
	@Test
	fun `exception with null message omits colon and message text`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			throw RuntimeException()
		}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { }
		)
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(!result.message.contains(": "))
	}
	
	@Test
	fun `exception with null cause does not include cause section`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {
			throw RuntimeException("some error")
		}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { }
		)
		
		assertIs<StreamProcessResult.Failed>(result)
		assertTrue(result.message.contains("RuntimeException"))
		assertTrue(result.message.contains("some error"))
		assertTrue(!result.message.contains("caused by"))
	}
	
	// endregion
	
	// region no emission
	
	@Test
	fun `process returns Failed when flow emits nothing`() = runTest {
		mockkObject(AgentChat)
		every { AgentChat.execute(any<AgentChatRequest>(), any()) } returns flow {}
		
		val result = AgentStreamProcessor.processRequest(
			createRequest(), UUID.randomUUID(),
			onContextUpdate = { }, onOutput = { }
		)
		
		assertIs<StreamProcessResult.Failed>(result)
	}
	
	// endregion
}
