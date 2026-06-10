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

import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.model.Model
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ToolCallHistoryImplTest {
	
	@Serializable
	private data class TestArgs(val cmd: String = "")
	
	private val mockModel = mockk<Model>(relaxed = true)
	
	private fun toolMessage(
		name: String = "bash-run",
		arguments: String = "{}",
		resultContent: String = "ok",
	) = AgentContext.Message.Tool(
		name = name,
		call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(),
			arguments = arguments,
			reason = "test",
			timestamp = Clock.System.now(),
			modelId = mockModel.id,
			
			validatedArgs = null,
		),
		callId = "call-1",
		result = AgentContext.Message.Tool.Result(
			content = resultContent,
			timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	private fun assistantMessage(content: String = "assistant") = AgentContext.Message.Assistant(
		content = content,
		modelId = mockModel.id,
		timestamp = Clock.System.now(),
		usageSnapshot = null,
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
	
	private fun env(context: AgentContext): AgentEnvironment {
		val e = mockk<AgentEnvironment>()
		every { e.context } returns MutableStateFlow(context)
		return e
	}
	
	@Test
	fun `getAll returns empty list for empty context`() {
		val context = AgentContext(null, null, null, null, null)
		val history = ToolCallHistoryImpl(env(context))
		
		val result = history.getAll("bash", TestArgs.serializer())
		assertTrue(result.isEmpty())
	}
	
	@Test
	fun `getAll extracts tool calls from currentRound`() {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(),
			turns = listOf(
				turn(
					listOf(
						toolMessage("bash-run", """{"cmd":"ls"}""", "file list"),
						toolMessage("bash-run", """{"cmd":"pwd"}""", "content"),
					)
				)
			),
		)
		val context = AgentContext(null, null, null, null, round)
		val history = ToolCallHistoryImpl(env(context))
		
		val result = history.getAll("bash", TestArgs.serializer())
		assertEquals(2, result.size)
		assertEquals("file list", result[0].resultContent)
		assertEquals("content", result[1].resultContent)
	}
	
	@Test
	fun `getAll extracts tool calls from historyRounds`() {
		val context = AgentContext(
			compactedRounds = null,
			systemPrompt = null,
			historyRounds = listOf(completedRound(listOf(toolMessage("bash-run", """{"cmd":"pwd"}""", "/home")))),
			summarizedMessage = null,
			currentRound = null,
		)
		val history = ToolCallHistoryImpl(env(context))
		
		val result = history.getAll("bash", TestArgs.serializer())
		assertEquals(1, result.size)
		assertEquals("pwd", result[0].args.cmd)
		assertEquals("/home", result[0].resultContent)
	}
	
	@Test
	fun `getAll extracts from both currentRound and historyRounds`() {
		val context = AgentContext(
			compactedRounds = null,
			systemPrompt = null,
			historyRounds = listOf(completedRound(listOf(toolMessage("bash-run", """{"cmd":"before"}""", "before")))),
			summarizedMessage = null,
			currentRound = AgentContext.CurrentRound(
				userMessage = userMessage(),
				turns = listOf(turn(listOf(toolMessage("bash-run", """{"cmd":"now"}""", "now")))),
			),
		)
		val history = ToolCallHistoryImpl(env(context))
		
		val result = history.getAll("bash", TestArgs.serializer())
		assertEquals(2, result.size)
	}
	
	@Test
	fun `getAll returns latest context`() {
		val flow = MutableStateFlow(AgentContext(null, null, null, null, null))
		val e = mockk<AgentEnvironment>()
		every { e.context } returns flow
		
		val history = ToolCallHistoryImpl(e)
		assertTrue(history.getAll("bash", TestArgs.serializer()).isEmpty())
		
		flow.value = AgentContext(
			null, null, null, null,
			AgentContext.CurrentRound(
				userMessage = userMessage(),
				turns = listOf(turn(listOf(toolMessage("bash-run", """{"cmd":"fresh"}""", "fresh")))),
			),
		)
		
		assertEquals(1, history.getAll("bash", TestArgs.serializer()).size)
	}
	
	@Test
	fun `getAll empty turns returns empty`() {
		val round = AgentContext.CurrentRound(
			userMessage = userMessage(),
			turns = listOf(AgentContext.Turn(assistantMessage = assistantMessage(), tools = emptyList())),
		)
		val context = AgentContext(null, null, null, null, round)
		val history = ToolCallHistoryImpl(env(context))
		
		val result = history.getAll("bash", TestArgs.serializer())
		assertTrue(result.isEmpty())
	}
}
