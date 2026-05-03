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

package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.llm.Model
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ToolCallHistoryImplTest {
	
	private val mockModel = mockk<Model>(relaxed = true)
	
	// region helpers
	
	private fun toolMessage(
		name: String = "bash_run",
		arguments: String = "{}",
		resultContent: String = "ok",
	) = AgentContext.Message.Tool(
		name = name,
		call = AgentContext.Message.Tool.Call(
			arguments = arguments,
			reason = "test",
			timestamp = Clock.System.now(),
			model = mockModel,
		),
		callId = "call-1",
		result = AgentContext.Message.Tool.Result(
			content = resultContent,
			timestamp = Clock.System.now(),
			status = AgentContext.Message.Tool.Result.Status.SUCCESS,
		),
	)
	
	private fun assistantMessage(content: String = "assistant") = AgentContext.Message.Assistant(
		content = content,
		model = mockModel,
		timestamp = Clock.System.now(),
		usage = null,
	)
	
	private fun userMessage(content: String = "user") = AgentContext.Message.User(
		content = content,
		timestamp = Clock.System.now(),
	)
	
	private fun turn(tools: List<AgentContext.Message.Tool>) = AgentContext.Turn(
		assistantMessage = assistantMessage(),
		tools = tools,
	)
	
	private fun completedRound(tools: List<AgentContext.Message.Tool>) = AgentContext.CompletedRound(
		userMessage = userMessage(),
		turns = listOf(turn(tools)),
		finalAssistantMessage = assistantMessage(),
	)
	// endregion
	
	@Test
	fun `getAll returns empty list for empty context`() {
		val context = AgentContext(null, null, null, null, null)
		val history = ToolCallHistoryImpl(context)
		
		val result = history.getAll()
		assertTrue(result.isEmpty())
	}
	
	@Test
	fun `getAll extracts tool calls from currentRound`() {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(),
			turns = listOf(
				turn(
					listOf(
						toolMessage("bash_run", """{"cmd":"ls"}""", "file list"),
						toolMessage("read_file", """{"path":"a.txt"}""", "content"),
					)
				)
			),
			assistantMessage = assistantMessage(),
		)
		val context = AgentContext(null, null, null, null, round)
		val history = ToolCallHistoryImpl(context)
		
		val result = history.getAll()
		assertEquals(2, result.size)
		assertEquals("bash_run", result[0].name)
		assertEquals("""{"cmd":"ls"}""", result[0].arguments)
		assertEquals("file list", result[0].resultContent)
		assertEquals("read_file", result[1].name)
		assertEquals("content", result[1].resultContent)
	}
	
	@Test
	fun `getAll extracts tool calls from historyRounds`() {
		val rounds = listOf(
			completedRound(
				listOf(
					toolMessage("bash_run", """{"cmd":"ls"}""", "output1"),
				)
			),
			completedRound(
				listOf(
					toolMessage("read_file", """{"path":"b.txt"}""", "output2"),
				)
			),
		)
		val context = AgentContext(null, null, rounds, null, null)
		val history = ToolCallHistoryImpl(context)
		
		val result = history.getAll()
		assertEquals(2, result.size)
		assertEquals("bash_run", result[0].name)
		assertEquals("output1", result[0].resultContent)
		assertEquals("read_file", result[1].name)
		assertEquals("output2", result[1].resultContent)
	}
	
	@Test
	fun `getAll combines historyRounds and currentRound`() {
		val historyRound = completedRound(
			listOf(
				toolMessage("bash_run", """{"cmd":"ls"}""", "history output"),
			)
		)
		val currentRound = AgentContext.CurrentRound(
			userMessage = userMessage(),
			turns = listOf(
				turn(
					listOf(
						toolMessage("read_file", """{"path":"x.txt"}""", "current output"),
					)
				)
			),
			assistantMessage = assistantMessage(),
		)
		val context = AgentContext(null, null, listOf(historyRound), null, currentRound)
		val history = ToolCallHistoryImpl(context)
		
		val result = history.getAll()
		assertEquals(2, result.size)
		assertEquals("bash_run", result[0].name)
		assertEquals("read_file", result[1].name)
	}
	
	@Test
	fun `getAll returns empty list when turns are null`() {
		val context = AgentContext(null, null, null, null, null)
		val history = ToolCallHistoryImpl(context)
		
		val result = history.getAll()
		assertTrue(result.isEmpty())
	}
	
	@Test
	fun `getAll returns the same list on repeated calls`() {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(),
			turns = listOf(
				turn(
					listOf(
						toolMessage("bash_run", """{"cmd":"ls"}""", "ok"),
					)
				)
			),
			assistantMessage = assistantMessage(),
		)
		val context = AgentContext(null, null, null, null, round)
		val history = ToolCallHistoryImpl(context)
		
		val result1 = history.getAll()
		val result2 = history.getAll()
		assertEquals(result1.size, result2.size)
		assertEquals(result1[0].name, result2[0].name)
	}
}
