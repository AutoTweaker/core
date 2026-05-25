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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.MutableAgentState
import io.github.autotweaker.core.domain.agent.chat.AgentStreamProcessor
import io.github.autotweaker.core.domain.agent.chat.AgentStreamProcessor.StreamProcessResult
import io.github.autotweaker.core.domain.agent.phase.PhaseResult
import io.github.autotweaker.core.domain.agent.phase.RequestLlmPhase
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RequestLlmPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private val _contextFlow = MutableStateFlow(AgentContext(null, null, null, null, null))
	private val statusLog = mutableListOf<AgentStatus>()
	
	@BeforeEach
	fun setUp() {
		mockkObject(AgentStreamProcessor)
		agentState = MutableAgentState()
		model = mockModel()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.currentModel } returns model
		every { env.currentFallbackModels } returns null
		every { env.currentThinking } returns false
		every { env.service } returns mockk<SettingService>(relaxed = true)
		every { env.tools } returns mockk(relaxed = true)
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		
		_contextFlow.value = AgentContext(null, null, null, null, null)
		every { env.context } returns _contextFlow
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { _contextFlow.value = transform(_contextFlow.value) }
		}
		coEvery { env.emitOutput(any()) } returns Unit
		statusLog.clear()
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
	}
	
	@AfterEach
	fun tearDown() {
		unmockkObject(AgentStreamProcessor)
	}
	
	@Test
	fun `StreamProcessResult Completed archives round and returns Done`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("done"),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		coEvery {
			AgentStreamProcessor.processRequest(
				any(),
				any(),
				any(),
				any()
			)
		} returns StreamProcessResult.Completed
		
		val result = RequestLlmPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		assertTrue(statusLog.contains(AgentStatus.FREE))
	}
	
	@Test
	fun `StreamProcessResult ToolCallsRequired returns Continue`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("calling tools"),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		coEvery {
			AgentStreamProcessor.processRequest(
				any(),
				any(),
				any(),
				any()
			)
		} returns StreamProcessResult.ToolCallsRequired(emptyList())
		
		val result = RequestLlmPhase.execute(env)
		
		assertEquals(PhaseResult.Continue, result)
	}
	
	@Test
	fun `StreamProcessResult Cancelled archives round and returns Done`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("cancelled"),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		coEvery {
			AgentStreamProcessor.processRequest(
				any(),
				any(),
				any(),
				any()
			)
		} returns StreamProcessResult.Cancelled
		
		val result = RequestLlmPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		assertTrue(statusLog.contains(AgentStatus.FREE))
	}
	
	@Test
	fun `StreamProcessResult Failed returns Error and sets ERROR status`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("failed"),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		coEvery {
			AgentStreamProcessor.processRequest(
				any(),
				any(),
				any(),
				any()
			)
		} returns StreamProcessResult.Failed("LLM error")
		
		val result = RequestLlmPhase.execute(env)
		
		assertEquals(PhaseResult.Error, result)
		assertTrue(statusLog.contains(AgentStatus.ERROR))
	}
	
	@Test
	fun `updates status to PROCESSING on entry`() = runTest {
		coEvery {
			AgentStreamProcessor.processRequest(
				any(),
				any(),
				any(),
				any()
			)
		} returns StreamProcessResult.Completed
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("done"),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		RequestLlmPhase.execute(env)
		
		assertTrue(statusLog.contains(AgentStatus.PROCESSING))
	}
	
	// region helpers
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), id = UUID.randomUUID()
		)
	}
	
	private fun userMessage(): AgentContext.Message.User =
		AgentContext.Message.User(content = "test", timestamp = Clock.System.now())
	
	private fun assistantMessage(content: String): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = content, modelId = model.id,
			timestamp = Clock.System.now(), usageSnapshot = null,
		)
	// endregion
}
