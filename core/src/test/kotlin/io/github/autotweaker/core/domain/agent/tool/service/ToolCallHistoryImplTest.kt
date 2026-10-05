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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.now
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.test.TestServices
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolCallHistoryImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	@Serializable
	private data class BashRequest(val command: String)
	
	private val msg = TestServices.messageBuilder()
	
	private fun ready() = Tool.ResolveResult.Ready(
		result = JsonPrimitive("{}"),
		request = { listOf(UiBlock.Text("request")) },
		executing = { listOf(UiBlock.Text("executing")) },
		cancelled = { listOf(UiBlock.Text("cancelled")) },
		rejected = { listOf(UiBlock.Text("rejected")) },
		failed = { listOf(UiBlock.Text("failed")) },
		timeout = { listOf(UiBlock.Text("timeout")) },
	)
	
	private suspend fun call(
		callId: String,
		resolvedRequest: JsonElement? = Json.parseToJsonElement("""{"command":"echo hi"}"""),
	) = msg.toolCall(
		timestamp = now(),
		callId = callId,
		callName = "bash-run",
		arguments = """{"cmd":"echo hi","reason":"because"}""",
		reason = "because",
		validatedToolName = "bash",
		validatedArgs = JsonPrimitive("{}"),
		resolvedRequest = resolvedRequest,
		presentation = null,
	)
	
	private suspend fun tool(
		callId: String,
		resolvedRequest: JsonElement? = Json.parseToJsonElement("""{"command":"echo hi"}"""),
		content: String = "done",
	) = AgentContextIndex.Turn.Tool(
		call(callId, resolvedRequest).ref(),
		msg.toolResult(
			callId = callId,
			content = content,
			data = JsonPrimitive(content),
			presentation = listOf(UiBlock.Text("executed command")),
			status = ToolResultStatus.SUCCESS,
		).ref(),
	)
	
	private suspend fun assistant() =
		msg.assistant(reasoning = null, content = "calling", model = UUID.randomUUID(), usage = null)
	
	private suspend fun round(tools: List<AgentContextIndex.Turn.Tool>) = AgentContextIndex.Round(
		userMsgRef = msg.user(MessageContent(content = "q".toContentPart())).ref(),
		turns = listOf(AgentContextIndex.Turn(assistant().ref(), tools)),
		assistantMsgRef = null,
	)
	
	@Test
	fun `getAll returns entries from history and current rounds`() = runTest {
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(round(listOf(tool("c1", content = "history result")))),
			currentRound = round(listOf(tool("c2", content = "current result"))),
		)
		
		val entries = ToolCallHistoryImpl(context, emptyList()).getAll(BashRequest.serializer(), String.serializer())
		
		assertEquals(2, entries.size)
		assertEquals(BashRequest("echo hi"), entries[0].first)
		assertEquals("history result", entries[0].second)
		assertEquals(BashRequest("echo hi"), entries[1].first)
		assertEquals("current result", entries[1].second)
	}
	
	@Test
	fun `getAll returns entries from unarchived tool calls`() = runTest {
		val context = RuntimeContext(null, null, null, null, round(emptyList()))
		val toolCall = AgentToolCallImpl(
			call("c1", Json.parseToJsonElement("""{"command":"echo hi"}""")), null,
			msg.toolResult(
				callId = "c1",
				content = "unarchived result",
				data = JsonPrimitive("unarchived result"),
				presentation = listOf(UiBlock.Text("executed command")),
				status = ToolResultStatus.SUCCESS,
			)
		)
		
		val entries =
			ToolCallHistoryImpl(context, listOf(toolCall)).getAll(BashRequest.serializer(), String.serializer())
		
		assertEquals(1, entries.size)
		assertEquals(BashRequest("echo hi"), entries[0].first)
		assertEquals("unarchived result", entries[0].second)
	}
	
	@Test
	fun `getAll skips tools without resolved request`() = runTest {
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(round(listOf(tool("c1", resolvedRequest = null)))),
			null,
		)
		
		val entries = ToolCallHistoryImpl(context, emptyList()).getAll(BashRequest.serializer(), String.serializer())
		
		assertTrue(entries.isEmpty())
	}
	
	@Test
	fun `getAll skips undecodable resolved requests`() = runTest {
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(round(listOf(tool("c1", resolvedRequest = JsonPrimitive("""{"wrong":"shape"}"""))))),
			null,
		)
		
		val entries = ToolCallHistoryImpl(context, emptyList()).getAll(BashRequest.serializer(), String.serializer())
		
		assertTrue(entries.isEmpty())
	}
	
	@Test
	fun `getAll returns empty for empty context`() {
		val context = RuntimeContext(null, null, null, null, null)
		
		assertTrue(
			ToolCallHistoryImpl(context, emptyList()).getAll(BashRequest.serializer(), String.serializer()).isEmpty()
		)
	}
}
