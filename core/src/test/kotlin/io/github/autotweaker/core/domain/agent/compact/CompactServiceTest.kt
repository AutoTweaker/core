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

package io.github.autotweaker.core.domain.agent.compact

import io.github.autotweaker.api.types.agent.AgentOutput
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.runner.AgentContextManager
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.model.Model
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Clock

class CompactServiceTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val agentId = UUID.randomUUID()
	private val model = AgentModel(
		model = mockk<Model>(relaxed = true),
		reasoning = null,
		summarize = mockk<Model>(relaxed = true),
		compact = mockk<Model>(relaxed = true),
		fallback = null,
	)
	
	private fun longSummary(): String = "summary ".repeat(10).trim()  // 62 字符 > MinSummaryLength(50)
	
	private fun compactService(onOutput: (RuntimeOutput) -> Unit = {}) =
		CompactService(agentId, onOutput)
	
	private fun managerWithHistory(): AgentContextManager {
		val manager = AgentContextManager(RuntimeContext(null, null, null, null, null))
		runBlocking {
			manager.beginRound(
				RuntimeContext.Message.User(
					id = UUID.randomUUID(),
					content = MessageContent(content = "question".toContentPart()),
					timestamp = Clock.System.now(),
				)
			)
			manager.applyThinking(
				RuntimeContext.Message.Assistant(
					id = UUID.randomUUID(),
					reasoning = null,
					content = "answer",
					modelId = UUID.randomUUID(),
					timestamp = Clock.System.now(),
					usage = null,
				),
				emptyList(),
				emptyList(),
			)
			manager.finalizeToolTurn()
			manager.archiveCurrentRound()
		}
		return manager
	}
	
	private fun assembledResult(content: String, usage: Usage? = null) = flow {
		emit(
			LlmResult(
				ChatResult.Assembled(
					message = ChatMessage.Assistant(content, Clock.System.now()),
					usage = usage,
				),
				model = UUID.randomUUID(),
			)
		)
	}
	
	
	private fun mockResilientChat(
		vararg results: Flow<LlmResult>,
		throwException: RuntimeException? = null,
	): AtomicInteger {
		val callCount = AtomicInteger(0)
		mockkObject(ResilientChat)
		coEvery {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
		} answers {
			callCount.incrementAndGet()
			throwException?.let { throw it }
			results[callCount.get() - 1]
		}
		return callCount
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(ResilientChat)
	}
	
	// region execute
	
	@Test
	fun `no history rounds returns without calling llm`() = runTest {
		val callCount = mockResilientChat()
		val manager = AgentContextManager(RuntimeContext(null, null, null, null, null))
		
		compactService().execute(model, manager)
		
		assertEquals(0, callCount.get())
	}
	
	@Test
	fun `successful compact applies summarized rounds`() = runTest {
		mockkObject(ResilientChat)
		val result = assembledResult("<summary>${longSummary()}</summary>")
		coEvery {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
		} returns result
		val manager = managerWithHistory()
		
		compactService().execute(model, manager)
		
		val context = manager.context.value
		assertNull(context.historyRounds)
		assertEquals(longSummary(), context.compactedRounds?.summarizedMessage?.content)
		assertEquals(1, context.compactedRounds?.rounds?.size)
	}
	
	@Test
	fun `failed compact retries up to max retries and reports error`() = runTest {
		val callCount = mockResilientChat(throwException = RuntimeException("llm down"))
		val manager = managerWithHistory()
		val outputs = mutableListOf<RuntimeOutput>()
		
		compactService { outputs.add(it) }.execute(model, manager)
		
		assertEquals(5, callCount.get())
		assertNull(manager.context.value.compactedRounds)
		assertEquals(1, manager.context.value.historyRounds?.size)
		assertTrue(
			outputs.any {
				it is RuntimeOutput.Output &&
						it.output is AgentOutput.Error &&
						it.output.type == AgentOutput.Error.Type.COMPACT
			}
		)
	}
	
	@Test
	fun `short summary is invalid and retried until valid`() = runTest {
		val callCount = mockResilientChat(
			assembledResult("<summary>short</summary>"),
			assembledResult("<summary>${longSummary()}</summary>"),
		)
		val manager = managerWithHistory()
		
		compactService().execute(model, manager)
		
		assertEquals(2, callCount.get())
		assertEquals(longSummary(), manager.context.value.compactedRounds?.summarizedMessage?.content)
	}
	
	@Test
	fun `usage from llm is collected into summarized message`() = runTest {
		mockkObject(ResilientChat)
		val result = assembledResult("<summary>${longSummary()}</summary>", usage = Usage(100, 50, 50))
		coEvery {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
		} returns result
		val manager = managerWithHistory()
		
		compactService().execute(model, manager)
		
		val usage = manager.context.value.compactedRounds?.summarizedMessage?.usage
		assertEquals(Usage(100, 50, 50), usage)
	}
	
	// endregion
}
