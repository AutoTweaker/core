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

import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ToolCallHistoryImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	@Serializable
	private data class BashRequest(val command: String)
	
	private fun tool(
		callId: String,
		resolvedRequest: JsonElement? = Json.parseToJsonElement("""{"command":"echo hi"}"""),
		content: String = "done",
	) = RuntimeContext.Message.Tool(
		callId = callId,
		call = RuntimeContext.Message.Tool.Call(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			callName = "bash-run",
			arguments = """{"cmd":"echo hi","reason":"because"}""",
			reason = "because",
			validatedToolName = "bash",
			validatedArgs = JsonPrimitive("{}"),
			resolvedRequest = resolvedRequest,
			presentation = null,
		),
		result = RuntimeContext.Message.Tool.Result(
			id = UUID.randomUUID(),
			content = content,
			data = null,
			presentation = listOf(UiBlock.Text("执行了命令")),
			timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	private fun completedRound(tools: List<RuntimeContext.Message.Tool>) =
		RuntimeContext.CompletedRound(
			userMessage = RuntimeContext.Message.User(
				id = UUID.randomUUID(),
				content = io.github.autotweaker.api.types.agent.MessageContent(content = "q"),
				timestamp = Clock.System.now(),
			),
			turns = listOf(RuntimeContext.Turn(assistant(), tools)),
			finalAssistantMessage = null,
		)
	
	private fun assistant() = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = "calling",
		modelId = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		usage = null,
	)
	
	@Test
	fun `getAll returns entries from history and current rounds`() {
		val historyTool = tool("c1", content = "history result")
		val currentTool = tool("c2", content = "current result")
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(completedRound(listOf(historyTool))),
			currentRound = RuntimeContext.CurrentRound(
				userMessage = RuntimeContext.Message.User(
					UUID.randomUUID(),
					io.github.autotweaker.api.types.agent.MessageContent(content = "q"),
					Clock.System.now(),
				),
				turns = listOf(RuntimeContext.Turn(assistant(), listOf(currentTool))),
				assistantMessage = null,
				pendingToolCalls = null,
			),
		)
		
		val entries = ToolCallHistoryImpl(context).getAll(BashRequest.serializer())
		
		assertEquals(2, entries.size)
		assertEquals(BashRequest("echo hi"), entries[0].request)
		assertEquals("history result", entries[0].resultContent)
		assertEquals(BashRequest("echo hi"), entries[1].request)
		assertEquals("current result", entries[1].resultContent)
	}
	
	@Test
	fun `getAll skips tools without resolved request`() {
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(
				completedRound(listOf(tool("c1", resolvedRequest = null)))
			),
			null,
		)
		
		val entries = ToolCallHistoryImpl(context).getAll(BashRequest.serializer())
		
		assertTrue(entries.isEmpty())
	}
	
	@Test
	fun `getAll skips undecodable resolved requests`() {
		val context = RuntimeContext(
			null, null, null,
			historyRounds = listOf(
				completedRound(listOf(tool("c1", resolvedRequest = JsonPrimitive("""{"wrong":"shape"}"""))))
			),
			null,
		)
		
		val entries = ToolCallHistoryImpl(context).getAll(BashRequest.serializer())
		
		assertTrue(entries.isEmpty())
	}
	
	@Test
	fun `getAll returns empty for empty context`() {
		val context = RuntimeContext(null, null, null, null, null)
		
		assertTrue(ToolCallHistoryImpl(context).getAll(BashRequest.serializer()).isEmpty())
	}
}
