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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.MutableAgentState
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
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

class HandleApprovalPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private val _contextFlow = MutableStateFlow(AgentContext(null, null, null, null, null))
	private val statusLog = mutableListOf<AgentStatus>()
	private val executedTools = mutableListOf<AgentContext.Message.Tool>()
	private val emittedOutputs = mutableListOf<AgentOutput>()
	
	private val executeTool: suspend (ToolCallValidator.ValidationResult.Success<*>, AgentContext.CurrentRound.PendingToolCall) -> AgentContext.Message.Tool =
		{ _, call ->
			executedTools.add(
				ContextPhase.buildToolResult(
					call,
					"executed",
					ToolResultStatus.SUCCESS
				)
			)
			executedTools.last()
		}
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		executedTools.clear()
		statusLog.clear()
		emittedOutputs.clear()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		every { env.status } returns AgentStatus.PROCESSING
		
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
	fun `returns Done when no pendingApproval`() = runTest {
		agentState.pendingApproval = null
		
		val result = HandleApprovalPhase.execute(env, emptyList(), executeTool)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `returns Done when no currentRound`() = runTest {
		agentState.pendingApproval = listOf(mockk(relaxed = true))
		_contextFlow.value = AgentContext(null, null, null, null, null)
		
		val result = HandleApprovalPhase.execute(env, emptyList(), executeTool)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `returns Done when no pendingToolCalls`() = runTest {
		agentState.pendingApproval = listOf(mockk(relaxed = true))
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMessage("ok"),
			pendingToolCalls = null,
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val result = HandleApprovalPhase.execute(env, emptyList(), executeTool)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `approved tool calls executeTool and writes turn to context`() = runTest {
		val call = pendingToolCall("c1")
		val vs = validationSuccess()
		agentState.pendingApproval = listOf(Tools.ToolCallResolveResult.NeedsApproval("c1", vs))
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = null))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertEquals(1, executedTools.size)
		assertNull(agentState.pendingApproval)
		assertNotNull(_contextFlow.value.currentRound?.turns)
		assertEquals(1, _contextFlow.value.currentRound!!.turns!!.size)
	}
	
	@Test
	fun `rejected tool writes cancelled turn to context`() = runTest {
		val call = pendingToolCall("c1")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = false, reason = "unsafe"))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertEquals(0, executedTools.size)
		assertNull(agentState.pendingApproval)
		val turns = _contextFlow.value.currentRound?.turns
		assertNotNull(turns)
		assertEquals(1, turns.size)
		assertEquals(ToolResultStatus.CANCELLED, turns[0].tools[0].result.status)
		assertEquals("Tool rejected: unsafe", turns[0].tools[0].result.content)
	}
	
	@Test
	fun `tool without approval stays pending`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
			Tools.ToolCallResolveResult.NeedsApproval("c2", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call1, call2),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = null))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Done, result)
		assertEquals(AgentStatus.WAITING, statusLog.last())
		assertEquals(1, agentState.pendingApproval!!.size)
		assertEquals("c2", agentState.pendingApproval!![0].callId)
		val pending = _contextFlow.value.currentRound?.pendingToolCalls
		assertNotNull(pending)
		assertEquals(1, pending.size)
		assertEquals("c2", pending[0].callId)
	}
	
	@Test
	fun `paused during processing stops remaining approvals`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		val call3 = pendingToolCall("c3")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
			Tools.ToolCallResolveResult.NeedsApproval("c2", validationSuccess()),
			Tools.ToolCallResolveResult.NeedsApproval("c3", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call1, call2, call3),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		every { env.status } returnsMany listOf(AgentStatus.PROCESSING, AgentStatus.PAUSED)
		
		val approvals = listOf(
			ToolApprove("c1", approved = true, reason = null),
			ToolApprove("c2", approved = true, reason = null),
			ToolApprove("c3", approved = true, reason = null),
		)
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Done, result)
		assertEquals(AgentStatus.WAITING, statusLog.last())
		assertEquals(1, agentState.pendingApproval!!.size)
		assertEquals("c3", agentState.pendingApproval!![0].callId)
	}
	
	@Test
	fun `throws when assistantMessage is null`() = runTest {
		val call = pendingToolCall("c1")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = null,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = null))
		assertFailsWith<IllegalArgumentException> {
			HandleApprovalPhase.execute(env, approvals, executeTool)
		}
	}
	
	@Test
	fun `appends to existing processedTools from prior phase`() = runTest {
		val call = pendingToolCall("c1")
		val preExistingTool = ContextPhase.buildToolResult(
			pendingToolCall("c0"), "previous output",
			ToolResultStatus.FAILURE
		)
		agentState.processedTools = listOf(preExistingTool)
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = null))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(agentState.pendingApproval)
		val turns = _contextFlow.value.currentRound?.turns
		assertNotNull(turns)
		assertEquals(1, turns.size)
		assertEquals(2, turns[0].tools.size)
	}
	
	
	@Test
	fun `approved with reason archives round and starts new round with reason`() = runTest {
		val call = pendingToolCall("c1")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = "allowed"))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertTrue(agentState.approvalReasons.isEmpty())
		assertNotNull(_contextFlow.value.historyRounds)
		assertEquals(1, _contextFlow.value.historyRounds!!.size)
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertEquals("allowed", cr.userMessage.content)
		assertNull(cr.turns)
	}
	
	@Test
	fun `approved without reason does not archive`() = runTest {
		val call = pendingToolCall("c1")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(ToolApprove("c1", approved = true, reason = null))
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(_contextFlow.value.historyRounds)
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertNotNull(cr.turns)
		assertEquals(1, cr.turns.size)
	}
	
	@Test
	fun `multiple approved calls with reasons join all reasons in new round`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		agentState.pendingApproval = listOf(
			Tools.ToolCallResolveResult.NeedsApproval("c1", validationSuccess()),
			Tools.ToolCallResolveResult.NeedsApproval("c2", validationSuccess()),
		)
		val assistantMsg = assistantMessage("assistant")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(), turns = null,
			assistantMessage = assistantMsg,
			pendingToolCalls = listOf(call1, call2),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(
			ToolApprove("c1", approved = true, reason = "reason1"),
			ToolApprove("c2", approved = true, reason = "reason2"),
		)
		val result = HandleApprovalPhase.execute(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertTrue(agentState.approvalReasons.isEmpty())
		assertNotNull(_contextFlow.value.historyRounds)
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertEquals("reason1\n---\nreason2", cr.userMessage.content)
	}
	
	// region helpers
	
	private fun validationSuccess() = ToolCallValidator.ValidationResult.Success(
		toolName = "bash", functionName = "run", reason = "needed",
		args = buildJsonObject {},
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
		callId = callId, assistantMessageId = UUID.randomUUID(), name = name, modelId = model.id,
		arguments = "{}", reason = "test reason", timestamp = Clock.System.now(),
		
		validatedArgs = null,
	)
	
	private fun userMessage(): AgentContext.Message.User =
		AgentContext.Message.User(content = "test", timestamp = Clock.System.now())
	
	private fun assistantMessage(content: String): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = content, modelId = model.id,
			timestamp = Clock.System.now(), usageSnapshot = null,
		)
	// endregion
}
