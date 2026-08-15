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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.runner.AgentContextManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentContextManagerTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private fun presentation(text: String = "工具调用") = listOf(UiBlock.Text(text))
	
	private fun ctx() = AgentContextManager(
		initial = RuntimeContext(null, null, null, null, null),
	)
	
	private fun user(content: String = "hello") = RuntimeContext.Message.User(
		id = UUID.randomUUID(),
		content = MessageContent(content = content),
		timestamp = Clock.System.now(),
	)
	
	private fun assistant(content: String = "reply") = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = content,
		modelId = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		usageSnapshot = null,
	)
	
	private fun pendingCall(callId: String = "c1") = RuntimeContext.CurrentRound.PendingToolCall(
		id = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		callId = callId,
		callName = "bash-run",
		arguments = """{"cmd":"echo"}""",
		reason = "because",
		validatedToolName = "bash",
		validatedArgs = JsonPrimitive("{}"),
		resolvedRequest = JsonPrimitive("{}"),
		presentation = presentation(),
	)
	
	private fun toolResult(callId: String = "c1", content: String = "done") = RuntimeContext.Message.Tool(
		callId = callId,
		call = RuntimeContext.Message.Tool.Call(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			callName = "bash-run",
			arguments = """{"cmd":"echo"}""",
			reason = "because",
			validatedToolName = "bash",
			validatedArgs = JsonPrimitive("{}"),
			resolvedRequest = JsonPrimitive("{}"),
			presentation = null,
		),
		result = RuntimeContext.Message.Tool.Result(
			id = UUID.randomUUID(),
			content = content,
			data = null,
			presentation = presentation(),
			timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	private suspend fun completeRound(
		manager: AgentContextManager,
		userMsg: RuntimeContext.Message.User = user(),
		assistantMsg: RuntimeContext.Message.Assistant = assistant(),
	) {
		manager.beginRound(userMsg)
		manager.applyThinking(assistantMsg, listOf(pendingCall()), listOf(toolResult()))
		manager.finalizeToolTurn()
		manager.archiveCurrentRound()
	}
	
	// region beginRound
	
	@Test
	fun `beginRound sets current round`() = runTest {
		val manager = ctx()
		val userMsg = user("question")
		
		manager.beginRound(userMsg)
		
		val round = manager.context.value.currentRound
		assertNotNull(round)
		assertEquals(userMsg, round.userMessage)
		assertNull(round.turns)
		assertNull(round.assistantMessage)
		assertNull(round.pendingToolCalls)
	}
	
	@Test
	fun `beginRound twice fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		
		assertFailsWith<IllegalStateException> { manager.beginRound(user("second")) }
	}
	
	@Test
	fun `beginRound with pending tool results fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant(), listOf(pendingCall()), listOf(toolResult()))
		
		assertFailsWith<IllegalStateException> { manager.beginRound(user("second")) }
	}
	
	// endregion
	
	// region applyThinking
	
	@Test
	fun `applyThinking sets assistant and pending calls`() = runTest {
		val manager = ctx()
		val asst = assistant()
		val pending = pendingCall()
		manager.beginRound(user())
		
		manager.applyThinking(asst, listOf(pending), listOf(toolResult()))
		
		val round = manager.context.value.currentRound
		assertNotNull(round)
		assertEquals(asst, round.assistantMessage)
		assertEquals(listOf(pending), round.pendingToolCalls)
	}
	
	@Test
	fun `applyThinking without round fails`() = runTest {
		val manager = ctx()
		
		assertFailsWith<IllegalArgumentException> {
			manager.applyThinking(assistant(), emptyList(), emptyList())
		}
	}
	
	@Test
	fun `applyThinking twice fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant(), emptyList(), emptyList())
		
		assertFailsWith<IllegalStateException> {
			manager.applyThinking(assistant("again"), emptyList(), emptyList())
		}
	}
	
	// endregion
	
	// region recordToolResult
	
	@Test
	fun `recordToolResult appends result for turn`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant(), listOf(pendingCall()), emptyList())
		val result = toolResult()
		
		manager.recordToolMessage(result)
		manager.finalizeToolTurn()
		
		val round = manager.context.value.currentRound
		assertNotNull(round)
		assertEquals(1, round.turns?.size)
		assertEquals(listOf(result), round.turns!![0].tools)
		assertNull(round.assistantMessage)
		assertNull(round.pendingToolCalls)
	}
	
	@Test
	fun `recordToolResult unknown callId fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant(), listOf(pendingCall("c1")), emptyList())
		
		assertFailsWith<IllegalStateException> { manager.recordToolMessage(toolResult("c9")) }
	}
	
	@Test
	fun `recordToolResult before thinking fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		
		assertFailsWith<IllegalStateException> { manager.recordToolMessage(toolResult()) }
	}
	
	// endregion
	
	// region finalizeToolTurn
	
	@Test
	fun `finalizeToolTurn accumulates multiple turns`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant("first"), listOf(pendingCall("c1")), emptyList())
		manager.recordToolMessage(toolResult("c1", "one"))
		manager.finalizeToolTurn()
		manager.applyThinking(assistant("second"), listOf(pendingCall("c2")), emptyList())
		manager.recordToolMessage(toolResult("c2", "two"))
		manager.finalizeToolTurn()
		
		val round = manager.context.value.currentRound
		assertNotNull(round)
		assertEquals(2, round.turns?.size)
		assertEquals("one", round.turns!![0].tools[0].result.content)
		assertEquals("two", round.turns[1].tools[0].result.content)
	}
	
	@Test
	fun `finalizeToolTurn without assistant fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		
		assertFailsWith<IllegalArgumentException> { manager.finalizeToolTurn() }
	}
	
	// endregion
	
	// region archiveCurrentRound
	
	@Test
	fun `archive without round is no-op`() = runTest {
		val manager = ctx()
		
		manager.archiveCurrentRound()
		
		assertNull(manager.context.value.currentRound)
		assertNull(manager.context.value.historyRounds)
	}
	
	@Test
	fun `archive empty round drops it`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		
		manager.archiveCurrentRound()
		
		assertNull(manager.context.value.currentRound)
		assertNull(manager.context.value.historyRounds)
	}
	
	@Test
	fun `archive with unprocessed pending calls synthesizes cancelled results`() = runTest {
		val manager = ctx()
		val pending = pendingCall("c1")
		manager.beginRound(user())
		manager.applyThinking(assistant(), listOf(pending), emptyList())
		
		manager.archiveCurrentRound()
		
		val completed = manager.context.value.historyRounds!!.single()
		val turn = completed.turns!!.single()
		assertEquals(1, turn.tools.size)
		assertEquals(ToolResultStatus.CANCELLED, turn.tools[0].result.status)
		assertTrue(turn.tools[0].result.content.contains("取消"))
		assertEquals(pending.callId, turn.tools[0].callId)
		assertEquals(pending.callName, turn.tools[0].call.callName)
		assertEquals(pending.resolvedRequest, turn.tools[0].call.resolvedRequest)
	}
	
	@Test
	fun `archive mixes processed and cancelled calls`() = runTest {
		val manager = ctx()
		manager.beginRound(user())
		manager.applyThinking(assistant(), listOf(pendingCall("c1"), pendingCall("c2")), emptyList())
		manager.recordToolMessage(toolResult("c1", "done"))
		
		manager.archiveCurrentRound()
		
		val tools = manager.context.value.historyRounds!!.single().turns!!.single().tools
		assertEquals(2, tools.size)
		assertEquals("c1", tools[0].callId)
		assertEquals(ToolResultStatus.SUCCESS, tools[0].result.status)
		assertEquals("c2", tools[1].callId)
		assertEquals(ToolResultStatus.CANCELLED, tools[1].result.status)
	}
	
	@Test
	fun `archive assistant without tools sets final assistant message`() = runTest {
		val manager = ctx()
		val asst = assistant("final words")
		manager.beginRound(user())
		manager.applyThinking(asst, emptyList(), emptyList())
		
		manager.archiveCurrentRound()
		
		val completed = manager.context.value.historyRounds!!.single()
		assertNull(completed.turns)
		assertEquals(asst, completed.finalAssistantMessage)
	}
	
	@Test
	fun `archive completed turn appends history round`() = runTest {
		val manager = ctx()
		val userMsg = user("question")
		val asst = assistant("answer")
		
		manager.beginRound(userMsg)
		manager.applyThinking(asst, listOf(pendingCall("c1")), emptyList())
		manager.recordToolMessage(toolResult("c1", "result"))
		manager.finalizeToolTurn()
		manager.archiveCurrentRound()
		
		val completed = manager.context.value.historyRounds!!.single()
		assertEquals(userMsg, completed.userMessage)
		assertEquals(1, completed.turns?.size)
		assertEquals(asst, completed.turns!![0].assistantMessage)
		assertEquals("result", completed.turns[0].tools[0].result.content)
		assertNull(completed.finalAssistantMessage)
		assertNull(manager.context.value.currentRound)
	}
	
	// endregion
	
	// region applyCompact
	
	@Test
	fun `applyCompact moves rounds into compacted rounds`() = runTest {
		val manager = ctx()
		completeRound(manager, user("q1"))
		completeRound(manager, user("q2"))
		val history = manager.context.value.historyRounds!!
		val summarized = RuntimeContext.SummarizedMessage(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			content = "summary",
			snapshots = null,
		)
		
		manager.applyCompact(summarized, history.take(1))
		
		val context = manager.context.value
		assertEquals(history.drop(1), context.historyRounds)
		assertEquals(history.take(1), context.compactedRounds?.rounds)
		assertEquals(summarized, context.compactedRounds?.summarizedMessage)
		assertNull(context.compactedRounds?.compactedRounds)
	}
	
	@Test
	fun `applyCompact unknown round fails`() = runTest {
		val manager = ctx()
		completeRound(manager)
		val foreign = RuntimeContext.CompletedRound(user("foreign"), null, assistant())
		
		assertFailsWith<IllegalStateException> {
			manager.applyCompact(
				RuntimeContext.SummarizedMessage(UUID.randomUUID(), Clock.System.now(), "s", null),
				listOf(foreign),
			)
		}
	}
	
	@Test
	fun `applyCompact without history fails`() = runTest {
		val manager = ctx()
		val summarized = RuntimeContext.SummarizedMessage(
			UUID.randomUUID(), Clock.System.now(), "s", null
		)
		
		assertFailsWith<IllegalArgumentException> { manager.applyCompact(summarized, emptyList()) }
	}
	
	// endregion
	
	// region updateInjections
	
	@Test
	fun `updateInjections sets and clears injections`() = runTest {
		val manager = ctx()
		val injection = io.github.autotweaker.api.types.agent.ContextInjection(
			tag = "context", content = "workspace data"
		)
		
		manager.updateInjections { listOf(injection) }
		assertEquals(listOf(injection), manager.context.value.injections)
		
		manager.updateInjections { null }
		assertNull(manager.context.value.injections)
	}
	
	// endregion
}
