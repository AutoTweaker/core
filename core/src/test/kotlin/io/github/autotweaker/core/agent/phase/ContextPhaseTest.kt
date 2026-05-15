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
import io.github.autotweaker.api.types.model.ModelId
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.MutableAgentState
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class ContextPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private val _contextFlow = MutableStateFlow(AgentContext(null, null, null, null, null))
	private val capturedOutputs = mutableListOf<AgentOutput>()
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		capturedOutputs.clear()
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		every { env.status } returns AgentStatus.FREE
		_contextFlow.value = AgentContext(null, null, null, null, null)
		every { env.context } returns _contextFlow
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { _contextFlow.value = transform(_contextFlow.value) }
		}
		coEvery { env.emitOutput(any()) } answers {
			capturedOutputs.add(firstArg())
		}
		justRun { env.updateStatus(any()) }
	}
	
	// region buildToolResult
	
	@Test
	fun `buildToolResult creates Tool message with given params`() {
		val call = pendingToolCall("c1", "bash_run", model)
		
		val result = ContextPhase.buildToolResult(
			call, "execution output",
			ToolResultStatus.SUCCESS
		)
		
		assertEquals("bash_run", result.name)
		assertEquals("c1", result.callId)
		assertEquals("execution output", result.result.content)
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		assertEquals(call.arguments, result.call.arguments)
		assertEquals(call.reason, result.call.reason)
	}
	
	@Test
	fun `buildToolResult produces FAILURE status`() {
		val call = pendingToolCall("c1")
		val result =
			ContextPhase.buildToolResult(call, "error", ToolResultStatus.FAILURE)
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
	}
	
	@Test
	fun `buildToolResult produces TIMEOUT status`() {
		val call = pendingToolCall("c1")
		val result =
			ContextPhase.buildToolResult(call, "timeout", ToolResultStatus.TIMEOUT)
		assertEquals(ToolResultStatus.TIMEOUT, result.result.status)
	}
	
	@Test
	fun `buildToolResult produces CANCELLED status`() {
		val call = pendingToolCall("c1")
		val result =
			ContextPhase.buildToolResult(call, "cancelled", ToolResultStatus.CANCELLED)
		assertEquals(ToolResultStatus.CANCELLED, result.result.status)
	}
	
	// endregion
	
	// region buildErrorTool
	
	@Test
	fun `buildErrorTool creates Tool with FAILURE status and error message`() {
		val call = pendingToolCall("c1")
		val result = ContextPhase.buildErrorTool(call, "Something went wrong")
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
		assertEquals("Something went wrong", result.result.content)
		assertEquals("c1", result.callId)
	}
	
	// endregion
	
	// region buildRejectedTool
	
	@Test
	fun `buildRejectedTool with feedback reason uses formatted message`() {
		val call = pendingToolCall("c1")
		val result = ContextPhase.buildRejectedTool(call, "unsafe operation", env)
		assertEquals(ToolResultStatus.CANCELLED, result.result.status)
		assertEquals("Tool rejected: unsafe operation", result.result.content)
	}
	
	@Test
	fun `buildRejectedTool without feedback reason uses default message`() {
		val call = pendingToolCall("c1")
		val result = ContextPhase.buildRejectedTool(call, null, env)
		assertEquals(ToolResultStatus.CANCELLED, result.result.status)
		assertEquals("Tool rejected", result.result.content)
	}
	
	// endregion
	
	// region keepPendingCalls
	
	@Test
	fun `keepPendingCalls filters pending calls by callIds`() = runTest {
		val call1 = pendingToolCall("c1")
		val call2 = pendingToolCall("c2")
		val call3 = pendingToolCall("c3")
		val round = currentRound(pendingToolCalls = listOf(call1, call2, call3))
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.keepPendingCalls(setOf("c1", "c3"), env::updateContext)
		
		val pending = _contextFlow.value.currentRound?.pendingToolCalls
		assertNotNull(pending)
		assertEquals(2, pending.size)
		assertEquals("c1", pending[0].callId)
		assertEquals("c3", pending[1].callId)
	}
	
	@Test
	fun `keepPendingCalls with empty set removes all pending calls`() = runTest {
		val round = currentRound(pendingToolCalls = listOf(pendingToolCall("c1")))
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.keepPendingCalls(emptySet(), env::updateContext)
		
		assertNull(_contextFlow.value.currentRound?.pendingToolCalls)
	}
	
	@Test
	fun `keepPendingCalls with no currentRound does nothing`() = runTest {
		_contextFlow.value = AgentContext(null, null, null, null, null)
		
		ContextPhase.keepPendingCalls(setOf("c1"), env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
	}
	
	@Test
	fun `keepPendingCalls with no pending calls does nothing`() = runTest {
		val round = currentRound(pendingToolCalls = null)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.keepPendingCalls(setOf("c1"), env::updateContext)
		
		assertNull(_contextFlow.value.currentRound?.pendingToolCalls)
	}
	
	// endregion
	
	// region writeToolTurn
	
	@Test
	fun `writeToolTurn returns Continue when tools are processed`() = runTest {
		val assistantMsg = assistantMessage("assistant content", model)
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output",
				ToolResultStatus.SUCCESS
			),
		)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(agentState.processedTools)
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertNull(cr.assistantMessage)
		assertNotNull(cr.turns)
		assertEquals(1, cr.turns.size)
	}
	
	@Test
	fun `writeToolTurn returns Done when no processed tools`() = runTest {
		agentState.processedTools = null
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `writeToolTurn returns Done when empty processed tools`() = runTest {
		agentState.processedTools = emptyList()
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `writeToolTurn returns Done when no currentRound`() = runTest {
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output",
				ToolResultStatus.SUCCESS
			),
		)
		_contextFlow.value = AgentContext(null, null, null, null, null)
		val assistantMsg = assistantMessage("assistant content", model)
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Done, result)
	}
	
	@Test
	fun `writeToolTurn accumulates turns across multiple calls`() = runTest {
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output1",
				ToolResultStatus.SUCCESS
			),
		)
		ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		val assistantMsg2 = assistantMessage("assistant content 2", model)
		_contextFlow.value = _contextFlow.value.copy(
			currentRound = _contextFlow.value.currentRound!!.copy(assistantMessage = assistantMsg2)
		)
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c2"),
				"output2",
				ToolResultStatus.SUCCESS
			),
		)
		ContextPhase.writeToolTurn(env, assistantMsg2, env::updateContext)
		
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertEquals(2, cr.turns!!.size)
	}
	
	@Test
	fun `writeToolTurn with approval reasons archives round and starts new round`() = runTest {
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output",
				ToolResultStatus.SUCCESS
			),
		)
		agentState.approvalReasons.addAll(listOf("reason1", "reason2"))
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Continue, result)
		assertTrue(agentState.approvalReasons.isEmpty())
		// old round archived
		assertNotNull(_contextFlow.value.historyRounds)
		assertEquals(1, _contextFlow.value.historyRounds!!.size)
		// new round with user message
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertEquals("reason1\n---\nreason2", cr.userMessage.content)
		assertNull(cr.turns)
	}
	
	@Test
	fun `writeToolTurn without approval reasons does not archive`() = runTest {
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output",
				ToolResultStatus.SUCCESS
			),
		)
		
		val result = ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		assertEquals(PhaseResult.Continue, result)
		assertNull(_contextFlow.value.historyRounds)
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertNotNull(cr.turns)
		assertEquals(1, cr.turns.size)
	}
	
	@Test
	fun `writeToolTurn with single approval reason no separator`() = runTest {
		val assistantMsg = assistantMessage("assistant content", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"output",
				ToolResultStatus.SUCCESS
			),
		)
		agentState.approvalReasons.add("only one reason")
		
		ContextPhase.writeToolTurn(env, assistantMsg, env::updateContext)
		
		val cr = _contextFlow.value.currentRound
		assertNotNull(cr)
		assertEquals("only one reason", cr.userMessage.content)
	}
	
	// endregion
	
	// region archiveCurrentRound
	
	@Test
	fun `archiveCurrentRound does nothing when no currentRound`() = runTest {
		_contextFlow.value = AgentContext(null, null, null, null, null)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
		assertNull(_contextFlow.value.historyRounds)
	}
	
	@Test
	fun `archiveCurrentRound discards round with only user message`() = runTest {
		val round = AgentContext.CurrentRound(userMessage = userMessage("test"), turns = null)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
		assertNull(_contextFlow.value.historyRounds)
	}
	
	@Test
	fun `archiveCurrentRound archives round with assistant message and no turns`() = runTest {
		val assistantMsg = assistantMessage("done", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		assertEquals(1, _contextFlow.value.historyRounds!!.size)
		val completed = _contextFlow.value.historyRounds!![0]
		assertEquals("test", completed.userMessage.content)
		assertEquals("done", completed.finalAssistantMessage?.content)
		assertNull(completed.turns)
	}
	
	@Test
	fun `archiveCurrentRound includes cancelled tools when pendingToolCalls exist`() = runTest {
		val assistantMsg = assistantMessage("assistant", model)
		val pendingCall = pendingToolCall("pending-1")
		val round = currentRound(assistantMessage = assistantMsg, pendingToolCalls = listOf(pendingCall))
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(agentState.pendingApproval)
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		val completed = _contextFlow.value.historyRounds!![0]
		assertNotNull(completed.turns)
		assertEquals(1, completed.turns.size)
		val cancelledTool = completed.turns[0].tools[0]
		assertEquals("Tool cancelled", cancelledTool.result.content)
		assertEquals(ToolResultStatus.CANCELLED, cancelledTool.result.status)
	}
	
	@Test
	fun `archiveCurrentRound clears pendingApproval and processedTools`() = runTest {
		agentState.pendingApproval = listOf(mockk(relaxed = true))
		agentState.processedTools = listOf(mockk(relaxed = true))
		val assistantMsg = assistantMessage("done", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(agentState.pendingApproval)
		assertNull(agentState.processedTools)
	}
	
	@Test
	fun `archiveCurrentRound merges processed tools with cancelled tools into turn`() = runTest {
		val assistantMsg = assistantMessage("assistant", model)
		val processedTool = ContextPhase.buildToolResult(
			pendingToolCall("call-1"), "output",
			ToolResultStatus.SUCCESS
		)
		agentState.processedTools = listOf(processedTool)
		val pendingCall = pendingToolCall("pending-1")
		val round = currentRound(assistantMessage = assistantMsg, pendingToolCalls = listOf(pendingCall))
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		val completed = _contextFlow.value.historyRounds!![0]
		assertNotNull(completed.turns)
		val tools = completed.turns[0].tools
		assertEquals(2, tools.size)
	}
	
	@Test
	fun `archiveCurrentRound appends to existing historyRounds`() = runTest {
		val existingCompleted = AgentContext.CompletedRound(
			userMessage = userMessage("old"),
			turns = null,
			finalAssistantMessage = assistantMessage("old reply", model),
		)
		val assistantMsg = assistantMessage("new", model)
		val round = currentRound(assistantMessage = assistantMsg)
		_contextFlow.value = AgentContext(null, null, historyRounds = listOf(existingCompleted), null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNotNull(_contextFlow.value.historyRounds)
		assertEquals(2, _contextFlow.value.historyRounds!!.size)
	}
	
	
	@Test
	fun `archiveCurrentRound with assistant null but has turns assigns null finalAssistantMessage`() = runTest {
		val priorTurn = AgentContext.Turn(
			assistantMessage = assistantMessage("prior assistant", model),
			tools = listOf(
				ContextPhase.buildToolResult(
					pendingToolCall("c0"),
					"prior output",
					ToolResultStatus.SUCCESS
				),
			),
		)
		val round = AgentContext.CurrentRound(
			userMessage = userMessage("test"),
			turns = listOf(priorTurn),
			assistantMessage = null,
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		val completed = _contextFlow.value.historyRounds!![0]
		assertNull(completed.finalAssistantMessage)
		assertNotNull(completed.turns)
		assertEquals(1, completed.turns.size)
	}
	
	@Test
	fun `archiveCurrentRound with existing turns merges them`() = runTest {
		val priorTurn = AgentContext.Turn(
			assistantMessage = assistantMessage("prior assistant", model),
			tools = listOf(
				ContextPhase.buildToolResult(
					pendingToolCall("c0"),
					"prior output",
					ToolResultStatus.SUCCESS
				),
			),
		)
		val assistantMsg = assistantMessage("current assistant", model)
		agentState.processedTools = listOf(
			ContextPhase.buildToolResult(
				pendingToolCall("c1"),
				"new output",
				ToolResultStatus.SUCCESS
			),
		)
		val round = AgentContext.CurrentRound(
			userMessage = userMessage("test"),
			turns = listOf(priorTurn),
			assistantMessage = assistantMsg,
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		val completed = _contextFlow.value.historyRounds!![0]
		assertNotNull(completed.turns)
		assertEquals(2, completed.turns.size)
		assertEquals("prior output", completed.turns[0].tools[0].result.content)
		assertEquals("new output", completed.turns[1].tools[0].result.content)
	}
	
	@Test
	fun `archiveCurrentRound with assistant null and pending calls keeps only prior turns`() = runTest {
		val priorTurn = AgentContext.Turn(
			assistantMessage = assistantMessage("prior assistant", model),
			tools = listOf(
				ContextPhase.buildToolResult(
					pendingToolCall("c0"),
					"prior",
					ToolResultStatus.SUCCESS
				),
			),
		)
		val pendingCall = pendingToolCall("pending-1")
		val round = AgentContext.CurrentRound(
			userMessage = userMessage("test"),
			turns = listOf(priorTurn),
			assistantMessage = null,
			pendingToolCalls = listOf(pendingCall),
		)
		_contextFlow.value = AgentContext(null, null, null, null, round)
		
		ContextPhase.archiveCurrentRound(env, env::updateContext)
		
		assertNull(_contextFlow.value.currentRound)
		assertNotNull(_contextFlow.value.historyRounds)
		val completed = _contextFlow.value.historyRounds!![0]
		assertNull(completed.finalAssistantMessage)
		assertNotNull(completed.turns)
		// only the prior turn survives; canceled tools are not archived when assistant is null
		assertEquals(1, completed.turns.size)
	}
	
	// endregion
	
	// region helpers
	
	private fun currentRound(
		assistantMessage: AgentContext.Message.Assistant? = null,
		pendingToolCalls: List<AgentContext.CurrentRound.PendingToolCall>? = null,
	) = AgentContext.CurrentRound(
		userMessage = userMessage("test"),
		turns = null,
		assistantMessage = assistantMessage,
		pendingToolCalls = pendingToolCalls,
	)
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), modelId = ModelId(
				"test-provider",
				"test-model"
			)
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
	
	private fun userMessage(content: String): AgentContext.Message.User =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	private fun assistantMessage(content: String, model: Model): AgentContext.Message.Assistant =
		AgentContext.Message.Assistant(
			content = content, model = model,
			timestamp = Clock.System.now(), usage = null,
		)
	// endregion
}
