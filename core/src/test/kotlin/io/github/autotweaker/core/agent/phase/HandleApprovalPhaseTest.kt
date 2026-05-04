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
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.agent.tool.Tools
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*
import kotlin.time.Clock

class HandleApprovalPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private var contextValue: AgentContext = AgentContext(null, null, null, null, null)
	private val statusLog = mutableListOf<AgentStatus>()
	private val executedTools = mutableListOf<AgentContext.Message.Tool>()
	private val emittedOutputs = mutableListOf<AgentOutput>()
	
	private val executeTool: suspend (ToolCallValidator.ValidationResult.Success, AgentContext.CurrentRound.PendingToolCall) -> AgentContext.Message.Tool =
		{ _, call ->
			executedTools.add(
				buildToolResult(call, "executed", AgentContext.Message.Tool.Result.Status.SUCCESS)
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
		every { env.agentState } returns agentState
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		every { env.status } returns AgentStatus.PROCESSING
		
		contextValue = AgentContext(null, null, null, null, null)
		every { env.context } answers { contextValue }
		every { env.context = any() } answers { contextValue = firstArg() }
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { contextValue = transform(contextValue) }
		}
		coEvery { env.emitOutput(any()) } answers { emittedOutputs.add(firstArg()) }
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
	}
	
	@Test
	fun `returns Done when no pendingApproval`() = runTest {
		agentState.pendingApproval = null
		
		val result = handleApprovalPhase(env, emptyList(), executeTool)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `returns Done when no currentRound`() = runTest {
		agentState.pendingApproval = listOf(mockk(relaxed = true))
		contextValue = AgentContext(null, null, null, null, null)
		
		val result = handleApprovalPhase(env, emptyList(), executeTool)
		
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val result = handleApprovalPhase(env, emptyList(), executeTool)
		
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertEquals(1, executedTools.size)
		assertNull(agentState.pendingApproval)
		assertNotNull(contextValue.currentRound?.turns)
		assertEquals(1, contextValue.currentRound!!.turns!!.size)
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = false, reason = "unsafe"))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertEquals(0, executedTools.size)
		assertNull(agentState.pendingApproval)
		val turns = contextValue.currentRound?.turns
		assertNotNull(turns)
		assertEquals(1, turns.size)
		assertEquals(AgentContext.Message.Tool.Result.Status.CANCELLED, turns[0].tools[0].result.status)
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Done, result)
		assertEquals(AgentStatus.WAITING, statusLog.last())
		assertEquals(1, agentState.pendingApproval!!.size)
		assertEquals("c2", agentState.pendingApproval!![0].callId)
		val pending = contextValue.currentRound?.pendingToolCalls
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
		contextValue = AgentContext(null, null, null, null, round)
		
		every { env.status } returnsMany listOf(AgentStatus.PROCESSING, AgentStatus.PAUSED)
		
		val approvals = listOf(
			AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null),
			AgentCommand.Message.ApproveToolCall.Approve("c2", approved = true, reason = null),
			AgentCommand.Message.ApproveToolCall.Approve("c3", approved = true, reason = null),
		)
		val result = handleApprovalPhase(env, approvals, executeTool)
		
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		assertFailsWith<IllegalArgumentException> {
			handleApprovalPhase(env, approvals, executeTool)
		}
	}
	
	@Test
	fun `appends to existing processedTools from prior phase`() = runTest {
		val call = pendingToolCall("c1")
		val preExistingTool = buildToolResult(
			pendingToolCall("c0"), "previous output",
			AgentContext.Message.Tool.Result.Status.FAILURE
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(agentState.pendingApproval)
		val turns = contextValue.currentRound?.turns
		assertNotNull(turns)
		assertEquals(1, turns.size)
		assertEquals(2, turns[0].tools.size)
	}
	
	@Test
	fun `emits ContextUpdate on writeToolTurn`() = runTest {
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		handleApprovalPhase(env, approvals, executeTool)
		
		val update = emittedOutputs.firstOrNull { it is AgentOutput.ContextUpdate }
		assertNotNull(update)
		assertEquals(AgentOutput.ContextUpdate.UpdateReason.TOOL, (update as AgentOutput.ContextUpdate).reason)
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = "allowed"))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertTrue(agentState.approvalReasons.isEmpty())
		assertNotNull(contextValue.historyRounds)
		assertEquals(1, contextValue.historyRounds!!.size)
		val cr = contextValue.currentRound
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = null))
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(contextValue.historyRounds)
		val cr = contextValue.currentRound
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
		contextValue = AgentContext(null, null, null, null, round)
		
		val approvals = listOf(
			AgentCommand.Message.ApproveToolCall.Approve("c1", approved = true, reason = "reason1"),
			AgentCommand.Message.ApproveToolCall.Approve("c2", approved = true, reason = "reason2"),
		)
		val result = handleApprovalPhase(env, approvals, executeTool)
		
		assertEquals(PhaseResult.Continue, result)
		assertTrue(agentState.approvalReasons.isEmpty())
		assertNotNull(contextValue.historyRounds)
		val cr = contextValue.currentRound
		assertNotNull(cr)
		assertEquals("reason1\n---\nreason2", cr.userMessage.content)
	}
	
	// region helpers
	
	private fun validationSuccess() = ToolCallValidator.ValidationResult.Success(
		toolName = "bash", functionName = "run", reason = "needed",
		arguments = buildJsonObject {},
	)
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(name = "test-model", provider = provider, modelInfo = mockk(relaxed = true))
	}
	
	private fun pendingToolCall(
		callId: String = "call-1",
		name: String = "test_function",
		model: Model = mockModel(),
	): AgentContext.CurrentRound.PendingToolCall = AgentContext.CurrentRound.PendingToolCall(
		callId = callId, name = name, model = model,
		arguments = "{}", reason = "test reason", timestamp = Clock.System.now(),
	)
	
	private fun userMessage(): AgentContext.Message.User =
		AgentContext.Message.User(content = "test", timestamp = Clock.System.now())
	
	private fun assistantMessage(content: String): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = content, model = model,
			timestamp = Clock.System.now(), usage = null,
		)
	// endregion
}
