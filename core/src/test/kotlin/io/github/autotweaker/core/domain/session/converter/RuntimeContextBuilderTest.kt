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

package io.github.autotweaker.core.domain.session.converter

import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.TestServices
import kotlinx.serialization.json.JsonNull
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class RuntimeContextBuilderTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	@Test
	fun `empty agent context rebuilds empty runtime context`() {
		val context = AgentContext.emptyContext("prompt")
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		assertNull(rebuilt.compactedRounds)
		assertNull(rebuilt.historyRounds)
		assertNull(rebuilt.currentRound)
		assertEquals("prompt", rebuilt.systemPrompt)
	}
	
	@Test
	fun `missing user message defaults to empty content`() {
		val id = UUID.randomUUID()
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null,
				listOf(AgentContextIndex.CompletedRound(id, null, null)),
				null,
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		val user = rebuilt.historyRounds!!.single().userMessage
		assertEquals(id, user.id)
		assertEquals(io.github.autotweaker.api.types.agent.MessageContent(), user.content)
	}
	
	@Test
	fun `missing assistant message defaults to random model id`() {
		val id = UUID.randomUUID()
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null,
				listOf(AgentContextIndex.CompletedRound(UUID.randomUUID(), null, id)),
				null,
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		assertNotNull(rebuilt.historyRounds!!.single().finalAssistantMessage?.modelId)
	}
	
	@Test
	fun `missing tool result defaults to failure status`() {
		val assistantId = UUID.randomUUID()
		val callId = UUID.randomUUID()
		val resultId = UUID.randomUUID()
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null,
				listOf(
					AgentContextIndex.CompletedRound(
						UUID.randomUUID(),
						listOf(
							AgentContextIndex.Turn(
								assistantId,
								listOf(AgentContextIndex.Turn.Tool(callId, resultId))
							)
						),
						null,
					)
				),
				null,
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		val result = rebuilt.historyRounds!!.single().turns!!.single().tools.single().result
		assertEquals(ToolResultStatus.FAILURE, result.status)
		assertEquals("", result.content)
	}
	
	@Test
	fun `missing pending call uses JsonNull for validated fields`() {
		val id = UUID.randomUUID()
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null, null,
				AgentContextIndex.CurrentRound(UUID.randomUUID(), null, null, listOf(id)),
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		val pending = rebuilt.currentRound!!.pendingToolCalls!!.single()
		assertEquals(JsonNull, pending.validatedArgs)
		assertEquals(JsonNull, pending.resolvedRequest)
		assertEquals("", pending.validatedToolName)
		assertEquals("", pending.callId)
	}
	
	@Test
	fun `missing compact message defaults to empty content`() {
		val id = UUID.randomUUID()
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				AgentContextIndex.CompactedRounds(null, emptyList(), id),
				null,
				null,
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, emptyMap())()
		
		assertEquals("", rebuilt.compactedRounds!!.summarizedMessage.content)
	}
	
	@Test
	fun `tool message callId comes from call message`() {
		val call = io.github.autotweaker.api.types.agent.AgentMessage.Tool.Call(
			id = UUID.randomUUID(),
			timestamp = Instant.fromEpochMilliseconds(1000),
			callId = "real-call-id",
			callName = "bash-run",
			arguments = "{}",
			reason = null,
			validatedToolName = null,
			validatedArgs = null,
			resolvedRequest = null,
		)
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null,
				listOf(
					AgentContextIndex.CompletedRound(
						UUID.randomUUID(),
						listOf(
							AgentContextIndex.Turn(
								UUID.randomUUID(),
								listOf(AgentContextIndex.Turn.Tool(call.id, UUID.randomUUID()))
							)
						),
						null,
					)
				),
				null,
			)
		)
		
		val rebuilt = RuntimeContextBuilder(context, mapOf(call.id to call))()
		
		val tool = rebuilt.historyRounds!!.single().turns!!.single().tools.single()
		assertEquals("real-call-id", tool.callId)
		assertEquals("bash-run", tool.call.callName)
	}
}
