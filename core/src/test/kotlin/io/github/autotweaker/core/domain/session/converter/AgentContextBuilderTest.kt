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

import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.textPart
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentContextBuilderTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private fun user(id: UUID = UUID.randomUUID(), content: String = "hello") = RuntimeContext.Message.User(
		id = id,
		content = MessageContent(content = content.textPart()),
		timestamp = Clock.System.now(),
	)
	
	private fun assistant(id: UUID = UUID.randomUUID(), content: String? = "reply") =
		RuntimeContext.Message.Assistant(
			id = id,
			reasoning = "thinking",
			content = content,
			modelId = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			usage = null,
		)
	
	private fun pendingCall(id: UUID = UUID.randomUUID()) = RuntimeContext.CurrentRound.PendingToolCall(
		id = id,
		timestamp = Clock.System.now(),
		callId = "c1",
		callName = "bash-run",
		arguments = """{"cmd":"echo"}""",
		reason = "because",
		validatedToolName = "bash",
		validatedArgs = JsonPrimitive("{}"),
		resolvedRequest = JsonPrimitive("""{"cmd":"echo"}"""),
		presentation = listOf(UiBlock.Text("请求执行命令")),
	)
	
	private fun tool(callId: String = "c1") = RuntimeContext.Message.Tool(
		callId = callId,
		call = RuntimeContext.Message.Tool.Call(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			callName = "bash-run",
			arguments = """{"cmd":"echo"}""",
			reason = "because",
			validatedToolName = "bash",
			validatedArgs = JsonPrimitive("{}"),
			resolvedRequest = JsonPrimitive("""{"cmd":"echo"}"""),
			presentation = null,
		),
		result = RuntimeContext.Message.Tool.Result(
			id = UUID.randomUUID(),
			content = "done",
			data = JsonPrimitive("""{"exit":0}"""),
			presentation = listOf(UiBlock.Text("执行了命令")),
			timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	private fun completedRound(userMsg: RuntimeContext.Message.User = user()) =
		RuntimeContext.CompletedRound(
			userMessage = userMsg,
			turns = listOf(RuntimeContext.Turn(assistant(), listOf(tool()))),
			finalAssistantMessage = null,
		)
	
	private fun fullRuntimeContext() = RuntimeContext(
		systemPrompt = "system prompt",
		injections = listOf(ContextInjection(tag = "ctx", content = "data")),
		compactedRounds = RuntimeContext.CompactedRounds(
			compactedRounds = null,
			rounds = listOf(completedRound(user(content = "compacted question"))),
			summarizedMessage = RuntimeContext.SummarizedMessage(
				UUID.randomUUID(), Clock.System.now(), "compacted summary", UUID.randomUUID(), null
			),
		),
		historyRounds = listOf(completedRound()),
		currentRound = RuntimeContext.CurrentRound(
			userMessage = user(content = "current"),
			turns = listOf(RuntimeContext.Turn(assistant(content = "answer"), listOf(tool("c2")))),
			assistantMessage = null,
			pendingToolCalls = listOf(pendingCall()),
		),
	)
	
	// region transform
	
	@Test
	fun `empty runtime context transforms to empty agent context`() {
		val old = AgentContext.emptyContext("old prompt")
		val (context, messages) = AgentContextBuilder(old, RuntimeContext(null, null, null, null, null), null)()
		
		assertNull(context.index.compactedRounds)
		assertNull(context.index.historyRounds)
		assertNull(context.index.currentRound)
		assertTrue(messages.isEmpty())
	}
	
	@Test
	fun `full context transforms with all message types`() {
		val old = AgentContext.emptyContext("old prompt")
		val (context, messages) = AgentContextBuilder(old, fullRuntimeContext(), null)()
		
		val allIds = context.index.ids()
		val messageTypes = messages.map { it::class }.toSet()
		assertTrue(AgentMessage.User::class in messageTypes)
		assertTrue(AgentMessage.Assistant::class in messageTypes)
		assertTrue(AgentMessage.Compact::class in messageTypes)
		assertTrue(AgentMessage.Tool.Call::class in messageTypes)
		assertTrue(AgentMessage.Tool.Result::class in messageTypes)
		assertEquals(allIds, messages.map { it.id }.toSet())
	}
	
	@Test
	fun `dropped messages computed from old index`() {
		val oldUser = user(content = "old question")
		val old = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				compactedRounds = null,
				historyRounds = listOf(
					AgentContextIndex.CompletedRound(oldUser.id, null, null)
				),
				currentRound = null,
			)
		)
		val new = RuntimeContext(null, null, null, null, null)
		
		val (context, _) = AgentContextBuilder(old, new, null)()
		
		assertEquals(setOf(oldUser.id), context.droppedMessages)
	}
	
	@Test
	fun `system prompt falls back to old context`() {
		val old = AgentContext.emptyContext("old prompt")
		val (context, _) = AgentContextBuilder(old, RuntimeContext(null, null, null, null, null), null)()
		
		assertEquals("old prompt", context.systemPrompt)
	}
	
	@Test
	fun `messages already in old context are not re-emitted`() {
		val oldUser = user(content = "persisted")
		val old = AgentContext.emptyContext("prompt").copy(
			index = AgentContextIndex(
				null,
				listOf(AgentContextIndex.CompletedRound(oldUser.id, null, null)),
				null,
			)
		)
		val new = RuntimeContext(
			null, null, null,
			historyRounds = listOf(
				RuntimeContext.CompletedRound(oldUser, null, null)
			),
			null,
		)
		
		val (_, messages) = AgentContextBuilder(old, new, null)()
		
		assertTrue(messages.isEmpty())
	}
	
	// endregion
	
	// region round-trip
	
	@Test
	fun `round trip preserves full runtime context`() {
		val old = AgentContext.emptyContext("old prompt")
		val original = fullRuntimeContext()
		val (context, messages) = AgentContextBuilder(old, original, null)()
		val messageMap = messages.associateBy { it.id }
		
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { ids -> ids.mapNotNull(messageMap::get) }() }
		
		assertEquals(original, rebuilt)
	}
	
	@Test
	fun `round trip preserves resolved request through pending calls`() {
		val old = AgentContext.emptyContext("old prompt")
		val original = RuntimeContext(
			"system prompt", null, null, null,
			RuntimeContext.CurrentRound(
				userMessage = user(content = "q"),
				turns = null,
				assistantMessage = assistant(content = "call"),
				pendingToolCalls = listOf(pendingCall()),
			),
		)
		val (context, messages) = AgentContextBuilder(old, original, null)()
		val messageMap = messages.associateBy { it.id }
		val (rebuilt, _) = runBlocking { RuntimeContextBuilder(context) { ids -> ids.mapNotNull(messageMap::get) }() }
		
		assertEquals(original, rebuilt)
		val pending = rebuilt.currentRound!!.pendingToolCalls!!.single()
		assertEquals(JsonPrimitive("""{"cmd":"echo"}"""), pending.resolvedRequest)
		assertEquals("bash", pending.validatedToolName)
	}
	
	@Test
	fun `round trip restores full compacted chain after truncation`() {
		val (context, messages) = multiLayerContext()
		
		// 运行时截断只保留最近 5 层，被丢弃的子树由 RuntimeContextBuilder 返回
		val (runtime, dropped) = runBlocking { RuntimeContextBuilder(context) { ids -> ids.mapNotNull(messages::get) }() }
		assertNotNull(dropped)
		
		// 持久化时用 dropped 子树补全，完整链不丢失
		val (saved, emitted) = AgentContextBuilder(context, runtime, dropped)()
		
		assertEquals(7, depth(saved.index.compactedRounds))
		// 被丢弃层的消息引用仍然保留在索引中
		assertTrue(messages.keys.all { it in saved.index.ids() })
		// 已在旧上下文中的消息不重复输出
		assertTrue(emitted.isEmpty())
	}
	
	// endregion
	
	private fun multiLayerContext(): Pair<AgentContext, Map<UUID, AgentMessage>> {
		val depth = 7
		val users = List(depth) { i ->
			AgentMessage.User(
				id = UUID.randomUUID(),
				timestamp = Clock.System.now(),
				content = MessageContent(content = "question $i".textPart()),
			)
		}
		val compacts = List(depth) { i ->
			AgentMessage.Compact(
				id = UUID.randomUUID(),
				timestamp = Clock.System.now(),
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
		return context to (users + compacts).associateBy { it.id }
	}
	
	private fun depth(cr: AgentContextIndex.CompactedRounds?): Int =
		if (cr == null) 0 else 1 + depth(cr.compactedRounds)
}
