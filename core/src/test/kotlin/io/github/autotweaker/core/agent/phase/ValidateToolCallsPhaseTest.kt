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

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.MutableAgentState
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.agent.tool.Tools
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class ValidateToolCallsPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var tools: Tools
	private lateinit var model: Model
	private val _contextFlow = MutableStateFlow(AgentContext(null, null, null, null, null))
	private val emittedOutputs = mutableListOf<AgentOutput>()
	private val statusLog = mutableListOf<AgentStatus>()
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		tools = mockk(relaxUnitFun = true)
		emittedOutputs.clear()
		statusLog.clear()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.tools } returns tools
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		_contextFlow.value = AgentContext(null, null, null, null, null)
		every { env.context } returns _contextFlow
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { _contextFlow.value = transform(_contextFlow.value) }
		}
		coEvery { env.emitOutput(any()) } answers { emittedOutputs.add(firstArg()) }
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
	}
	
	@Test
	fun `returns Done when no currentRound`() = runTest {
		_contextFlow.value = AgentContext(null, null, null, null, null)
		
		val result = ValidateToolCallsPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `returns Done when no pendingToolCalls`() = runTest {
		val round = currentRound(pendingToolCalls = null, assistantMessage = assistantMessage())
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val result = ValidateToolCallsPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `throws when assistantMessage is null`() = runTest {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = null,
			pendingToolCalls = listOf(pendingToolCall("c1")),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		assertFailsWith<IllegalArgumentException> {
			ValidateToolCallsPhase.execute(env)
		}
	}
	
	@Test
	fun `all parse failures writes error tools as turn and continues`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		val round = currentRound(
			pendingToolCalls = listOf(call1, call2),
			assistantMessage = assistantMessage(),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		every { tools.resolveToolCalls(any(), any()) } returns listOf(
			Tools.ToolCallResolveResult.ParseFailure("c1", "Invalid JSON"),
			Tools.ToolCallResolveResult.ParseFailure("c2", "Function not found"),
		)
		
		val result = ValidateToolCallsPhase.execute(env)
		
		assertEquals(PhaseResult.Continue, result)
		val turns = _contextFlow.value.currentRound?.turns
		assertNotNull(turns)
		assertEquals(1, turns.size)
		assertEquals(2, turns[0].tools.size)
		assertEquals(ToolResultStatus.FAILURE, turns[0].tools[0].result.status)
		assertEquals("Invalid JSON", turns[0].tools[0].result.content)
		assertEquals(ToolResultStatus.FAILURE, turns[0].tools[1].result.status)
		assertEquals("Function not found", turns[0].tools[1].result.content)
		assertNull(agentState.pendingApproval)
		assertNull(_contextFlow.value.currentRound?.pendingToolCalls)
	}
	
	@Test
	fun `all need approval emits ToolCallRequest and waits`() = runTest {
		val call1 = pendingToolCall("c1", "bash_run")
		val call2 = pendingToolCall("c2", "read_file")
		val round = currentRound(
			pendingToolCalls = listOf(call1, call2),
			assistantMessage = assistantMessage(),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val vs1 = validationSuccess("bash", "run")
		val vs2 = validationSuccess("read", "file")
		every { tools.resolveToolCalls(any(), any()) } returns listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", vs1),
			Tools.ToolCallResolveResult.NeedsApproval("c2", vs2),
		)
		
		val result = ValidateToolCallsPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
		assertEquals(AgentStatus.WAITING, statusLog.last())
		assertEquals(2, agentState.pendingApproval!!.size)
		val req = emittedOutputs.firstOrNull { it is AgentOutput.ToolRequest } as? AgentOutput.ToolRequest
		assertNotNull(req)
		assertEquals(2, req.requests.size)
	}
	
	@Test
	fun `mixed failures and approvals handles both`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		val round = currentRound(
			pendingToolCalls = listOf(call1, call2),
			assistantMessage = assistantMessage(),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		every { tools.resolveToolCalls(any(), any()) } returns listOf(
			Tools.ToolCallResolveResult.ParseFailure("c1", "Bad JSON"),
			Tools.ToolCallResolveResult.NeedsApproval("c2", validationSuccess()),
		)
		
		val result = ValidateToolCallsPhase.execute(env)
		
		assertEquals(PhaseResult.Done, result)
		assertEquals(AgentStatus.WAITING, statusLog.last())
		assertNotNull(agentState.processedTools)
		assertEquals(1, agentState.processedTools!!.size)
		assertEquals(
			ToolResultStatus.FAILURE,
			agentState.processedTools!![0].result.status
		)
		assertEquals("Bad JSON", agentState.processedTools!![0].result.content)
		assertNotNull(agentState.pendingApproval)
		assertEquals(1, agentState.pendingApproval!!.size)
		assertEquals("c2", agentState.pendingApproval!![0].callId)
		val pending = _contextFlow.value.currentRound?.pendingToolCalls
		assertNotNull(pending)
		assertEquals(1, pending.size)
		assertEquals("c2", pending[0].callId)
	}
	
	@Test
	fun `updates status to PROCESSING on entry`() = runTest {
		val round = currentRound(
			pendingToolCalls = listOf(pendingToolCall("c1")),
			assistantMessage = assistantMessage(),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		every { tools.resolveToolCalls(any(), any()) } returns listOf(
			Tools.ToolCallResolveResult.ParseFailure("c1", "error"),
		)
		
		ValidateToolCallsPhase.execute(env)
		
		assertTrue(statusLog.contains(AgentStatus.PROCESSING))
	}
	
	@Test
	fun `single approval emits ToolCallRequest with correct call details`() = runTest {
		val call = pendingToolCall("c1", "bash_run")
		val round = currentRound(
			pendingToolCalls = listOf(call),
			assistantMessage = assistantMessage(),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		every { tools.resolveToolCalls(any(), any()) } returns listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		
		ValidateToolCallsPhase.execute(env)
		
		val req = emittedOutputs.firstOrNull { it is AgentOutput.ToolRequest } as? AgentOutput.ToolRequest
		assertNotNull(req)
		assertEquals(1, req.requests.size)
		assertEquals("bash_run", req.requests[0].name)
	}
	
	// region helpers
	
	private fun currentRound(
		assistantMessage: AgentContext.Message.Assistant,
		pendingToolCalls: List<AgentContext.CurrentRound.PendingToolCall>? = null,
	) = AgentContext.CurrentRound(
		userMessage = userMessage(), turns = null,
		assistantMessage = assistantMessage, pendingToolCalls = pendingToolCalls,
	)
	
	private fun validationSuccess(
		toolName: String = "bash",
		functionName: String = "run",
	) = ToolCallValidator.ValidationResult.Success(
		toolName = toolName, functionName = functionName,
		reason = "needed", arguments = buildJsonObject {},
	)
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), id = UUID.randomUUID()
		)
	}
	
	private fun pendingToolCall(
		callId: String = "call-1",
		name: String = "test_function",
		model: Model = mockModel(),
	): AgentContext.CurrentRound.PendingToolCall = AgentContext.CurrentRound.PendingToolCall(
		callId = callId, assistantMessageId = UUID.randomUUID(), name = name, model = model,
		arguments = "{}", reason = "test reason", timestamp = Clock.System.now(),
	)
	
	private fun userMessage(): AgentContext.Message.User =
		AgentContext.Message.User(content = "test", timestamp = Clock.System.now())
	
	private fun assistantMessage(): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = "ok", model = model,
			timestamp = Clock.System.now(), usageSnapshot = null,
		)
	// endregion
}
