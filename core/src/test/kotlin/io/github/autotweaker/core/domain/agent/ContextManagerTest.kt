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

import io.github.autotweaker.api.get
import io.github.autotweaker.api.now
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.ToolCallStatus
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.ContextInjection
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.runner.ContextManager
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import io.github.autotweaker.core.test.TestServices
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.*

class ContextManagerTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val msg = TestServices.messageBuilder()
	
	private fun presentation(text: String = "tool call") = listOf(UiBlock.Text(text))
	
	private fun ctx() = ContextManager(
		initial = RuntimeContext(null, null, null, null, null),
		msg = msg,
	)
	
	private suspend fun user(content: String = "hello") =
		msg.user(MessageContent(content = content.toContentPart()))
	
	private suspend fun assistant(content: String? = "reply") =
		msg.assistant(reasoning = null, content = content, model = UUID.randomUUID(), usage = null)
	
	private fun ready() = Tool.ResolveResult.Ready(
		result = JsonPrimitive("{}"),
		request = { presentation("request") },
		executing = { presentation("executing") },
		cancelled = { presentation("cancelled") },
		rejected = { presentation("rejected") },
		failed = { presentation("failed") },
		timeout = { presentation("timeout") },
	)
	
	private suspend fun call(callId: String = "c1") = msg.toolCall(
		timestamp = now(),
		callId = callId,
		callName = "bash-run",
		arguments = """{"cmd":"echo"}""",
		reason = "because",
		validatedToolName = "bash",
		validatedArgs = JsonPrimitive("{}"),
		resolvedRequest = JsonPrimitive("{}"),
		presentation = presentation(),
	)
	
	private suspend fun result(callId: String = "c1", content: String = "done") = msg.toolResult(
		callId = callId,
		content = content,
		data = null,
		presentation = presentation(),
		status = ToolResultStatus.SUCCESS,
	)
	
	private suspend fun pendingCall(callId: String = "c1") =
		AgentToolCallImpl(call(callId), ready(), null)
	
	private suspend fun finishedCall(callId: String = "c1", content: String = "done") =
		AgentToolCallImpl(call(callId), null, result(callId, content))
	
	private fun ContextManager.round() = context.value.currentRound!!
	
	private fun ContextManager.history() = context.value.historyRounds!!
	
	private suspend fun completeRound(
		manager: ContextManager,
		userMsg: AgentMessage.User,
		assistantMsg: AgentMessage.Assistant,
	) {
		manager.beginRound(userMsg.ref())
		manager.applyThinking(assistantMsg.ref(), listOf(finishedCall()))
		manager.finalizeToolTurn()
		manager.archiveCurrentRound()
	}
	
	// region beginRound
	
	@Test
	fun `beginRound sets current round`() = runTest {
		val manager = ctx()
		val userMsg = user("question")
		
		manager.beginRound(userMsg.ref())
		
		val round = manager.round()
		assertEquals(userMsg.ref(), round.userMsgRef)
		assertEquals(userMsg, round.userMessage.getOrNull())
		assertNull(round.turns)
		assertNull(round.assistantMsgRef)
		assertNull(round.assistantMessage)
	}
	
	@Test
	fun `beginRound twice fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		
		assertFailsWith<IllegalStateException> { manager.beginRound(user("second").ref()) }
	}
	
	@Test
	fun `beginRound with pending tool results fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), listOf(pendingCall()))
		
		assertFailsWith<IllegalStateException> { manager.beginRound(user("second").ref()) }
	}
	
	// endregion
	
	// region applyThinking
	
	@Test
	fun `applyThinking sets assistant and tool calls`() = runTest {
		val manager = ctx()
		val asst = assistant()
		val pending = pendingCall()
		manager.beginRound(user().ref())
		
		manager.applyThinking(asst.ref(), listOf(pending))
		
		val round = manager.round()
		assertEquals(asst.ref(), round.assistantMsgRef)
		assertEquals(asst, round.assistantMessage?.getOrNull())
		assertEquals(asst.id, manager.toolCalls?.first)
		assertEquals(listOf(pending), manager.toolCalls?.second)
	}
	
	@Test
	fun `applyThinking with empty tool calls fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		
		assertFailsWith<IllegalArgumentException> {
			manager.applyThinking(assistant().ref(), emptyList())
		}
	}
	
	@Test
	fun `applyThinking without round fails`() = runTest {
		val manager = ctx()
		
		assertFailsWith<IllegalStateException> {
			manager.applyThinking(assistant().ref(), null)
		}
	}
	
	@Test
	fun `applyThinking twice fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), null)
		
		assertFailsWith<IllegalStateException> {
			manager.applyThinking(assistant("again").ref(), null)
		}
	}
	
	// endregion
	
	// region finishToolCall
	
	@Test
	fun `finished tool call is archived into turn`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		val pending = pendingCall()
		manager.applyThinking(assistant().ref(), listOf(pending))
		val toolResult = result()
		
		pending.finish(toolResult)
		manager.finalizeToolTurn()
		
		val round = manager.round()
		assertEquals(1, round.turns?.size)
		val tool = round.turns!![0].tools.single()
		assertEquals(toolResult.ref(), tool.resultRef)
		assertEquals(toolResult, tool.result.getOrNull())
		assertNull(round.assistantMsgRef)
		assertNull(manager.toolCalls)
	}
	
	@Test
	fun `finishing the same call twice fails`() = runTest {
		val pending = pendingCall()
		
		pending.finish(result())
		
		assertFailsWith<IllegalStateException> { pending.finish(result()) }
	}
	
	@Test
	fun `finalizeToolTurn with unfinished calls fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), listOf(pendingCall("c1"), pendingCall("c2")))
		
		assertFailsWith<IllegalStateException> { manager.finalizeToolTurn() }
	}
	
	// endregion
	
	// region cancelPending
	
	@Test
	fun `cancelPending converts pending calls to cancelled results`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), listOf(pendingCall("c1"), pendingCall("c2")))
		
		manager.cancelPending()
		
		val cancelled = manager.toolCalls!!.second
		assertTrue(cancelled.all { it.status.value == ToolCallStatus.FINISHED })
		assertEquals(listOf("c1", "c2"), cancelled.map { it.result!!.callId })
		assertTrue(cancelled.all { it.result!!.status == ToolResultStatus.CANCELLED })
		assertTrue(cancelled.all { it.result!!.content == ToolSettings.CancelledPending().get() })
	}
	
	@Test
	fun `cancelPending keeps existing finished results`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		val immediate = finishedCall("c0", "done")
		val pending = pendingCall("c1")
		manager.applyThinking(assistant().ref(), listOf(immediate, pending))
		
		manager.cancelPending()
		
		assertEquals(ToolResultStatus.SUCCESS, immediate.result!!.status)
		assertEquals(ToolResultStatus.CANCELLED, pending.result!!.status)
	}
	
	@Test
	fun `cancelPending uses resolved cancelled presentation`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		val pending = pendingCall("c1")
		manager.applyThinking(assistant().ref(), listOf(pending))
		
		manager.cancelPending()
		
		assertEquals(presentation("cancelled"), pending.result!!.presentation)
	}
	
	@Test
	fun `cancelPending without round is no-op`() = runTest {
		val manager = ctx()
		
		manager.cancelPending()
		
		assertNull(manager.context.value.currentRound)
	}
	
	@Test
	fun `cancelPending without pending calls is no-op`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		
		manager.cancelPending()
		
		assertNull(manager.toolCalls)
	}
	
	// endregion
	
	// region finalizeToolTurn
	
	@Test
	fun `finalizeToolTurn accumulates multiple turns`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant("first").ref(), listOf(pendingCall("c1")))
		manager.toolCalls!!.second.single().finish(result("c1", "one"))
		manager.finalizeToolTurn()
		manager.applyThinking(assistant("second").ref(), listOf(pendingCall("c2")))
		manager.toolCalls!!.second.single().finish(result("c2", "two"))
		manager.finalizeToolTurn()
		
		val turns = manager.round().turns!!
		assertEquals(2, turns.size)
		assertEquals("one", turns[0].tools[0].result.getOrNull()?.content)
		assertEquals("two", turns[1].tools[0].result.getOrNull()?.content)
	}
	
	@Test
	fun `finalizeToolTurn without assistant fails`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		
		assertFailsWith<IllegalStateException> { manager.finalizeToolTurn() }
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
		manager.beginRound(user().ref())
		
		manager.archiveCurrentRound()
		
		assertNull(manager.context.value.currentRound)
		assertNull(manager.context.value.historyRounds)
	}
	
	@Test
	fun `archive with unprocessed pending calls synthesizes cancelled results`() = runTest {
		val manager = ctx()
		val pending = pendingCall("c1")
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), listOf(pending))
		
		manager.archiveCurrentRound()
		
		val completed = manager.history().single()
		val tool = completed.turns!!.single().tools.single()
		val result = tool.result.getOrNull()!!
		assertEquals(ToolResultStatus.CANCELLED, result.status)
		assertEquals(ToolSettings.CancelledPending().get(), result.content)
		assertEquals(pending.call.callId, tool.call.getOrNull()?.callId)
		assertEquals(pending.call.callName, tool.call.getOrNull()?.callName)
		assertEquals(pending.call.resolvedRequest, tool.call.getOrNull()?.resolvedRequest)
	}
	
	@Test
	fun `archive mixes processed and cancelled calls`() = runTest {
		val manager = ctx()
		manager.beginRound(user().ref())
		manager.applyThinking(assistant().ref(), listOf(pendingCall("c1"), pendingCall("c2")))
		manager.toolCalls!!.second.first().finish(result("c1", "done"))
		
		manager.archiveCurrentRound()
		
		val tools = manager.history().single().turns!!.single().tools
		assertEquals(2, tools.size)
		assertEquals("c1", tools[0].call.getOrNull()?.callId)
		assertEquals(ToolResultStatus.SUCCESS, tools[0].result.getOrNull()?.status)
		assertEquals("c2", tools[1].call.getOrNull()?.callId)
		assertEquals(ToolResultStatus.CANCELLED, tools[1].result.getOrNull()?.status)
	}
	
	@Test
	fun `archive assistant without tools sets final assistant message`() = runTest {
		val manager = ctx()
		val asst = assistant("final words")
		manager.beginRound(user().ref())
		manager.applyThinking(asst.ref(), null)
		
		manager.archiveCurrentRound()
		
		val completed = manager.history().single()
		assertNull(completed.turns)
		assertEquals(asst.ref(), completed.assistantMsgRef)
		assertEquals(asst, completed.assistantMessage?.getOrNull())
	}
	
	@Test
	fun `archive completed turn appends history round`() = runTest {
		val manager = ctx()
		val userMsg = user("question")
		val asst = assistant("answer")
		
		manager.beginRound(userMsg.ref())
		manager.applyThinking(asst.ref(), listOf(pendingCall("c1")))
		manager.toolCalls!!.second.single().finish(result("c1", "result"))
		manager.finalizeToolTurn()
		manager.archiveCurrentRound()
		
		val completed = manager.history().single()
		assertEquals(userMsg.ref(), completed.userMsgRef)
		assertEquals(1, completed.turns?.size)
		assertEquals(asst.ref(), completed.turns!![0].assistantMsgRef)
		assertEquals(asst, completed.turns!![0].assistantMessage.getOrNull())
		assertEquals("result", completed.turns!![0].tools[0].result.getOrNull()?.content)
		assertNull(completed.assistantMsgRef)
		assertNull(manager.context.value.currentRound)
	}
	
	@Test
	fun `archive with assistant and finished calls archives turn`() = runTest {
		val manager = ctx()
		val asst = assistant("answer")
		manager.beginRound(user("question").ref())
		manager.applyThinking(asst.ref(), listOf(finishedCall("c1", "result")))
		
		manager.archiveCurrentRound()
		
		val completed = manager.history().single()
		assertEquals(1, completed.turns?.size)
		assertEquals(asst.ref(), completed.turns!![0].assistantMsgRef)
		assertEquals("result", completed.turns!![0].tools[0].result.getOrNull()?.content)
		assertNull(completed.assistantMsgRef)
	}
	
	// endregion
	
	// region applyCompact
	
	@Test
	fun `applyCompact moves rounds into compacted rounds`() = runTest {
		val manager = ctx()
		completeRound(manager, user("q1"), assistant())
		completeRound(manager, user("q2"), assistant())
		val history = manager.history()
		val summary = msg.compact(content = "summary", model = UUID.randomUUID(), usage = null)
		
		manager.applyCompact(summary.ref(), history.take(1))
		
		val context = manager.context.value
		assertEquals(history.drop(1), context.historyRounds)
		assertEquals(history.take(1), context.compactedRounds?.rounds)
		assertEquals(summary.ref(), context.compactedRounds?.summaryMsgRef)
		assertEquals(summary, context.compactedRounds?.summaryMessage?.getOrNull())
		assertNull(context.compactedRounds?.compactedRounds)
	}
	
	@Test
	fun `applyCompact unknown round fails`() = runTest {
		val manager = ctx()
		completeRound(manager, user(), assistant())
		val foreign = AgentContextIndex.Round(user("foreign").ref(), null, assistant().ref())
		
		assertFailsWith<IllegalStateException> {
			manager.applyCompact(msg.compact("s", UUID.randomUUID(), null).ref(), listOf(foreign))
		}
	}
	
	@Test
	fun `applyCompact without history fails`() = runTest {
		val manager = ctx()
		
		assertFailsWith<IllegalStateException> {
			manager.applyCompact(msg.compact("s", UUID.randomUUID(), null).ref(), emptyList())
		}
	}
	
	// endregion
	
	// region updateInjections
	
	@Test
	fun `updateInjections sets and clears injections`() = runTest {
		val manager = ctx()
		val injection = ContextInjection(
			tag = "context", content = "workspace data"
		)
		
		manager.updateInjections { listOf(injection) }
		assertEquals(listOf(injection), manager.context.value.injections)
		
		manager.updateInjections { null }
		assertNull(manager.context.value.injections)
	}
	
	// endregion
}
