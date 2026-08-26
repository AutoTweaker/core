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
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import java.util.*
import kotlin.test.*
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
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
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
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
		val user = rebuilt.historyRounds!!.single().userMessage
		assertEquals(id, user.id)
		assertEquals(MessageContent(), user.content)
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
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
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
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
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
				AgentContextIndex.CurrentRound(UUID.randomUUID(), null, null, null, listOf(id)),
			)
		)
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
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
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { emptyList() }() }
		
		assertEquals("", rebuilt.compactedRounds!!.summarizedMessage.content)
	}
	
	@Test
	fun `tool message callId comes from call message`() {
		val call = AgentMessage.Tool.Call(
			id = UUID.randomUUID(),
			timestamp = Instant.fromEpochMilliseconds(1000),
			callId = "real-call-id",
			callName = "bash-run",
			arguments = "{}",
			reason = null,
			validatedToolName = null,
			validatedArgs = null,
			resolvedRequest = null,
			presentation = null,
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
		
		val calls = mapOf(call.id to call)
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { ids -> ids.mapNotNull(calls::get) }() }
		
		val tool = rebuilt.historyRounds!!.single().turns!!.single().tools.single()
		assertEquals("real-call-id", tool.callId)
		assertEquals("bash-run", tool.call.callName)
	}
	
	@Test
	fun `compacted rounds beyond KeepCompactedRounds are truncated and not loaded`() {
		val chain = multiLayerChain()
		val queried = mutableSetOf<UUID>()
		val loader: suspend (Set<UUID>) -> List<AgentMessage> = { ids ->
			queried += ids
			ids.mapNotNull(chain.messages::get)
		}
		
		val (rebuilt, dropped) = runBlocking { RuntimeContextBuilder(chain.context, loader)() }
		
		// 默认 KeepCompactedRounds=5：只保留最近 5 层
		assertEquals(5, depth(rebuilt.compactedRounds))
		// 保留层的消息被按需加载，被丢弃层的消息不会被查询
		val keptIds = chain.userIds.take(5) + chain.compactIds.take(5)
		val droppedIds = chain.userIds.drop(5) + chain.compactIds.drop(5)
		assertTrue(queried.containsAll(keptIds))
		assertTrue(queried.none { it in droppedIds })
		// 保留层内容正确恢复，截断点位于第 5 层之后
		assertEquals("summary 0", rebuilt.compactedRounds!!.summarizedMessage.content)
		val deepestKept = deepest(rebuilt.compactedRounds)
		assertEquals(chain.userIds[4], deepestKept.rounds.single().userMessage.id)
		assertNull(deepestKept.compactedRounds)
		// 被丢弃的子树返回给调用方，供持久化补全
		assertNotNull(dropped)
		assertEquals(chain.compactIds[5], dropped.summarizedMessage)
		assertEquals(chain.userIds[5], dropped.rounds.single().userMessage)
		assertEquals(chain.compactIds[6], dropped.compactedRounds!!.summarizedMessage)
		assertNull(dropped.compactedRounds!!.compactedRounds)
	}
	
	private class MultiLayerChain(
		val context: AgentContext,
		val messages: Map<UUID, AgentMessage>,
		val userIds: List<UUID>,
		val compactIds: List<UUID>,
	)
	
	private fun multiLayerChain(): MultiLayerChain {
		val depth = 7
		val users = List(depth) { i ->
			AgentMessage.User(
				id = UUID.randomUUID(),
				timestamp = Instant.fromEpochMilliseconds(1000L * i),
				content = MessageContent(content = "question $i".toContentPart()),
			)
		}
		val compacts = List(depth) { i ->
			AgentMessage.Compact(
				id = UUID.randomUUID(),
				timestamp = Instant.fromEpochMilliseconds(1000L * i),
				content = "summary $i",
				model = UUID.randomUUID(),
				usage = null,
			)
		}
		var node: AgentContextIndex.CompactedRounds? = null
		for (i in depth - 1 downTo 0) {
			node = AgentContextIndex.CompactedRounds(
				compactedRounds = node,
				rounds = listOf(AgentContextIndex.CompletedRound(users[i].id, null, null)),
				summarizedMessage = compacts[i].id,
			)
		}
		val context = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(node, null, null)
		)
		return MultiLayerChain(
			context,
			(users + compacts).associateBy { it.id },
			users.map { it.id },
			compacts.map { it.id },
		)
	}
	
	private fun depth(cr: AgentContextIndex.CompactedRounds?): Int =
		if (cr == null) 0 else 1 + depth(cr.compactedRounds)
	
	private fun depth(cr: RuntimeContext.CompactedRounds?): Int =
		if (cr == null) 0 else 1 + depth(cr.compactedRounds)
	
	private fun deepest(cr: RuntimeContext.CompactedRounds): RuntimeContext.CompactedRounds =
		cr.compactedRounds?.let(::deepest) ?: cr
}
