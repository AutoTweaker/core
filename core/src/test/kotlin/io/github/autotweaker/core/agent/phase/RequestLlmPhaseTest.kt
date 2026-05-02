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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.*
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock

class RequestLlmPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var streamProcessor: AgentStreamProcessor
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private var contextValue: AgentContext = AgentContext(null, null, null, null, null)
	private val statusLog = mutableListOf<AgentStatus>()
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		streamProcessor = mockk()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentState } returns agentState
		every { env.currentModel } returns model
		every { env.currentFallbackModels } returns null
		every { env.currentThinking } returns false
		every { env.tools } returns mockk(relaxed = true)
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		
		contextValue = AgentContext(null, null, null, null, null)
		every { env.context } answers { contextValue }
		every { env.context = any() } answers { contextValue = firstArg() }
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { contextValue = transform(contextValue) }
		}
		coEvery { env.emitOutput(any()) } returns Unit
		statusLog.clear()
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
	}
	
	@Test
	fun `StreamProcessResult Completed archives round and returns Done`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("done"),
		)
		contextValue = AgentContext(null, null, null, null, round)
		coEvery { streamProcessor.process(any()) } returns StreamProcessResult.Completed
		
		val result = requestLlmPhase(env, streamProcessor)
		
		assertEquals(PhaseResult.Done, result)
		assertNull(contextValue.currentRound)
		assertNotNull(contextValue.historyRounds)
		assertTrue(statusLog.contains(AgentStatus.FREE))
	}
	
	@Test
	fun `StreamProcessResult ToolCallsRequired returns Continue`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("calling tools"),
		)
		contextValue = AgentContext(null, null, null, null, round)
		coEvery { streamProcessor.process(any()) } returns StreamProcessResult.ToolCallsRequired(emptyList())
		
		val result = requestLlmPhase(env, streamProcessor)
		
		assertEquals(PhaseResult.Continue, result)
	}
	
	@Test
	fun `StreamProcessResult Cancelled archives round and returns Done`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("cancelled"),
		)
		contextValue = AgentContext(null, null, null, null, round)
		coEvery { streamProcessor.process(any()) } returns StreamProcessResult.Cancelled
		
		val result = requestLlmPhase(env, streamProcessor)
		
		assertEquals(PhaseResult.Done, result)
		assertNull(contextValue.currentRound)
		assertNotNull(contextValue.historyRounds)
		assertTrue(statusLog.contains(AgentStatus.FREE))
	}
	
	@Test
	fun `StreamProcessResult Failed returns Error and sets ERROR status`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("failed"),
		)
		contextValue = AgentContext(null, null, null, null, round)
		coEvery { streamProcessor.process(any()) } returns StreamProcessResult.Failed("LLM error")
		
		val result = requestLlmPhase(env, streamProcessor)
		
		assertEquals(PhaseResult.Error, result)
		assertTrue(statusLog.contains(AgentStatus.ERROR))
	}
	
	@Test
	fun `updates status to PROCESSING on entry`() = runTest {
		coEvery { streamProcessor.process(any()) } returns StreamProcessResult.Completed
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("done"),
		)
		contextValue = AgentContext(null, null, null, null, round)
		
		requestLlmPhase(env, streamProcessor)
		
		assertTrue(statusLog.contains(AgentStatus.PROCESSING))
	}
	
	// region helpers
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(name = "test-model", provider = provider, modelInfo = mockk(relaxed = true))
	}
	
	private fun userMessage(): AgentContext.Message.User =
		AgentContext.Message.User(content = "test", timestamp = Clock.System.now())
	
	private fun assistantMessage(content: String): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = content, model = model,
			timestamp = Clock.System.now(), usage = null,
		)
	// endregion
}
