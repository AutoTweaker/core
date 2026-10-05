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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.ToolCallStatus
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import io.github.autotweaker.core.test.TestServices
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoundContextTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val msg = TestServices.messageBuilder()
	
	private fun presentation(text: String = "tool call") = listOf(UiBlock.Text(text))
	
	private fun ready(request: JsonElement = JsonPrimitive("{}")) = Tool.ResolveResult.Ready(
		result = request,
		request = { presentation("requested command") },
		executing = { presentation("executing command") },
		cancelled = { presentation("cancelled command") },
		rejected = { presentation("rejected command") },
		failed = { presentation("failed command") },
		timeout = { presentation("timed out command") },
	)
	
	private fun rawCall(id: String = "c1", name: String = "bash-run") =
		ChatMessage.Assistant.ToolCall(id = id, name = name, arguments = """{"cmd":"echo"}""")
	
	private suspend fun assistant() =
		msg.assistant(reasoning = null, content = "calling tools", model = UUID.randomUUID(), usage = null)
	
	private suspend fun context(): ContextManager {
		val manager = ContextManager(RuntimeContext(null, null, null, null, null), msg)
		manager.beginRound(msg.user(MessageContent(content = "question".toContentPart())).ref())
		return manager
	}
	
	private suspend fun apply(
		manager: ContextManager,
		assistantMessage: AgentMessage.Assistant,
		vararg calls: Pair<ChatMessage.Assistant.ToolCall, ResolveResult>,
	) = RoundContext(manager, msg).applyThinking(
		ThinkingStage.Result(assistantMessage, calls.toList().orNull())
	)
	
	private fun ContextManager.calls() = checkNotNull(toolCalls).second
	private fun ContextManager.call(callId: String = "c1") = calls().single { it.call.callId == callId }
	
	private fun ContextManager.round() = checkNotNull(context.value.currentRound)
	
	// region parse failure
	
	@Test
	fun `parse failure keeps raw call fields`() = runTest {
		val manager = context()
		val asst = assistant()
		val call = rawCall()
		val failure = ResolveResult.ParseFailure("missing reason", presentation("parse failed"))
		
		apply(manager, asst, call to failure)
		
		val pending = manager.call()
		assertEquals("c1", pending.call.callId)
		assertEquals("bash-run", pending.call.callName)
		assertEquals(call.arguments, pending.call.arguments)
		assertNull(pending.call.reason)
		assertNull(pending.call.validatedToolName)
		assertNull(pending.call.validatedArgs)
		assertNull(pending.call.resolvedRequest)
		assertNull(pending.call.presentation)
		assertEquals(asst.timestamp, pending.call.timestamp)
		
		val result = pending.result!!
		assertEquals(ToolResultStatus.FAILURE, result.status)
		assertEquals("missing reason", result.content)
		assertEquals(presentation("parse failed"), result.presentation)
		assertEquals(ToolCallStatus.FINISHED, pending.status.value)
		assertNull(pending.resolved)
	}
	
	// endregion
	
	// region resolve failure
	
	@Test
	fun `resolve failure carries validated fields`() = runTest {
		val manager = context()
		val failure = ResolveResult.ResolveFailure(
			toolName = "bash",
			reason = "no such file",
			validatedArgs = JsonPrimitive("{}"),
			errorMessage = "文件test.txt不存在或访问被拒绝",
			presentation = presentation("resolve failed"),
		)
		
		apply(manager, assistant(), rawCall() to failure)
		
		val pending = manager.call()
		assertEquals("no such file", pending.call.reason)
		assertEquals("bash", pending.call.validatedToolName)
		assertEquals(JsonPrimitive("{}"), pending.call.validatedArgs)
		assertNull(pending.call.resolvedRequest)
		assertNull(pending.call.presentation)
		
		val result = pending.result!!
		assertEquals(ToolResultStatus.FAILURE, result.status)
		assertEquals("文件test.txt不存在或访问被拒绝", result.content)
		assertEquals(presentation("resolve failed"), result.presentation)
		assertNull(pending.resolved)
	}
	
	// endregion
	
	// region activation
	
	@Test
	fun `activation returns success with activation message`() = runTest {
		val manager = context()
		val call = rawCall()
		val activation = ResolveResult.Activation(
			targetName = "bash",
			reason = "please activate",
			validatedArgs = JsonPrimitive("{}"),
			presentation = presentation("activated"),
			message = "tool activated",
		)
		
		apply(manager, assistant(), call to activation)
		
		val pending = manager.call()
		assertEquals(ToolSettings.ACTIVE_TOOL_NAME, pending.call.validatedToolName)
		assertEquals("please activate", pending.call.reason)
		assertNull(pending.call.resolvedRequest)
		
		val result = pending.result!!
		assertEquals(ToolResultStatus.SUCCESS, result.status)
		assertEquals("tool activated", result.content)
		assertEquals(presentation("activated"), result.presentation)
		assertNull(pending.resolved)
	}
	
	// endregion
	
	// region needs approval
	
	@Test
	fun `needs approval waits for approval with resolved request`() = runTest {
		val manager = context()
		val request = JsonPrimitive("""{"cmd":"echo"}""")
		val call = rawCall()
		val approval = ResolveResult.NeedsApproval(
			toolName = "bash",
			reason = "because",
			validatedArgs = JsonPrimitive("{}"),
			resolveResult = ready(request),
		)
		
		apply(manager, assistant(), call to approval)
		
		val pending = manager.call()
		assertEquals("because", pending.call.reason)
		assertEquals("bash", pending.call.validatedToolName)
		assertEquals(JsonPrimitive("{}"), pending.call.validatedArgs)
		assertEquals(request, pending.call.resolvedRequest)
		assertEquals(presentation("requested command"), pending.call.presentation)
		assertNull(pending.result)
		assertNotNull(pending.resolved)
		assertEquals(ToolCallStatus.PENDING, pending.status.value)
	}
	
	// endregion
	
	// region 顺序与空值
	
	@Test
	fun `tool calls keep their order`() = runTest {
		val manager = context()
		val parse = ResolveResult.ParseFailure("parse error", presentation())
		val resolve =
			ResolveResult.ResolveFailure("bash", "rejected", JsonPrimitive("{}"), "resolve error", presentation())
		val activation = ResolveResult.Activation(
			targetName = "bash",
			reason = "activate",
			validatedArgs = JsonPrimitive("{}"),
			presentation = presentation(),
			message = "activated",
		)
		
		apply(
			manager, assistant(),
			rawCall("c1") to parse,
			rawCall("c2") to resolve,
			rawCall("c3") to activation,
		)
		
		val calls = manager.calls()
		assertEquals(listOf("c1", "c2", "c3"), calls.map { it.call.callId })
		assertEquals("parse error", calls[0].result!!.content)
		assertEquals("resolve error", calls[1].result!!.content)
		assertEquals("activated", calls[2].result!!.content)
	}
	
	@Test
	fun `assistant without tool calls leaves tool calls empty`() = runTest {
		val manager = context()
		val asst = assistant()
		
		RoundContext(manager, msg).applyThinking(ThinkingStage.Result(asst, null))
		
		assertNull(manager.toolCalls)
		assertEquals(asst.ref(), manager.round().assistantMsgRef)
	}
	
	// endregion
}
