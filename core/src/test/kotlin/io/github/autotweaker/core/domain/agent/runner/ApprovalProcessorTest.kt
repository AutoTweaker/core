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

import io.github.autotweaker.api.now
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.test.TestServices
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalProcessorTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val model = mockk<AgentModel>()
	private val msg = TestServices.messageBuilder()
	
	private fun presentation(text: String = "requested command") = listOf(UiBlock.Text(text))
	
	private fun ready() = Tool.ResolveResult.Ready(
		result = JsonPrimitive("{}"),
		request = { presentation("requested command") },
		executing = { presentation("executing command") },
		cancelled = { presentation("cancelled command") },
		rejected = { presentation("rejected command") },
		failed = { presentation("failed command") },
		timeout = { presentation("timed out command") },
	)
	
	private suspend fun call(callId: String) = msg.toolCall(
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
	
	private suspend fun pendingCall(callId: String) = AgentToolCallImpl(call(callId), ready(), null)
	
	private suspend fun manager(callIds: List<String> = listOf("c1", "c2")): ContextManager {
		val manager = ContextManager(RuntimeContext(null, null, null, null, null), msg)
		manager.beginRound(msg.user(MessageContent(content = "hello".toContentPart())).ref())
		val assistant =
			msg.assistant(reasoning = null, content = "calling tools", model = UUID.randomUUID(), usage = null)
		manager.applyThinking(assistant.ref(), callIds.map { pendingCall(it) })
		return manager
	}
	
	private fun toolMock() = mockk<ToolCallingStage>().also { tool ->
		coEvery { tool.execute(any(), any(), any(), any()) } coAnswers {
			val call = firstArg<AgentToolCallImpl>()
			call.finish(
				msg.toolResult(
					callId = call.call.callId,
					content = "tool done",
					data = null,
					presentation = presentation("executed command"),
					status = ToolResultStatus.SUCCESS,
				)
			)
		}
	}
	
	private fun processor(
		ctx: ContextManager,
		tool: ToolCallingStage,
		shouldBreak: Boolean = false,
	) = ApprovalProcessor(
		ctx, tool, msg, MutableStateFlow(AgentStatus.FREE), MutableStateFlow(shouldBreak)
	)
	
	// region 批准
	
	@Test
	fun `approved call executes tool and returns reason`() = runTest {
		val ctx = manager(listOf("c1"))
		val tool = toolMock()
		val processor = processor(ctx, tool)
		
		processor.approvalChannel.send(ToolApprove(ctx.toolCalls!!.second.single().call.id, reason = "go ahead"))
		val reasons = processor.process(model)
		
		assertEquals(listOf("go ahead"), reasons)
		coVerify(exactly = 1) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		val tools = ctx.context.value.currentRound?.turns?.single()?.tools
		assertEquals(1, tools?.size)
		assertEquals(ToolResultStatus.SUCCESS, tools!![0].result.getOrNull()?.status)
		assertEquals("tool done", tools[0].result.getOrNull()?.content)
	}
	
	@Test
	fun `approved call without reason returns empty reasons`() = runTest {
		val ctx = manager(listOf("c1"))
		val tool = toolMock()
		val processor = processor(ctx, tool)
		
		processor.approvalChannel.send(ToolApprove(ctx.toolCalls!!.second.single().call.id, reason = null))
		val reasons = processor.process(model)
		
		assertTrue(reasons.isEmpty())
	}
	
	// endregion
	
	// region 拒绝
	
	@Test
	fun `rejected call records rejected result without executing`() = runTest {
		val ctx = manager(listOf("c1"))
		val tool = toolMock()
		val processor = processor(ctx, tool)
		
		processor.approvalChannel.send(
			ToolApprove(ctx.toolCalls!!.second.single().call.id, reason = "no thanks", approved = false)
		)
		val reasons = processor.process(model)
		
		assertTrue(reasons.isEmpty())
		coVerify(exactly = 0) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		val result = ctx.context.value.currentRound?.turns?.single()?.tools?.single()?.result?.getOrNull()
		assertEquals(ToolResultStatus.REJECTED, result?.status)
		assertTrue(result!!.content.contains("no thanks"))
	}
	
	// endregion
	
	// region 乱序
	
	@Test
	fun `out-of-order approvals are stashed until their turn`() = runTest {
		val ctx = manager()
		val tool = toolMock()
		val processor = processor(ctx, tool)
		val calls = ctx.toolCalls!!.second
		
		processor.approvalChannel.send(ToolApprove(calls[1].call.id, reason = "second first"))
		processor.approvalChannel.send(ToolApprove(calls[0].call.id, reason = "first"))
		val reasons = processor.process(model)
		
		// 审批结果收集在 ConcurrentHashMap 中，reasons 的顺序不做保证
		assertEquals(setOf("first", "second first"), reasons.toSet())
		coVerify(exactly = 2) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		assertEquals(2, ctx.context.value.currentRound?.turns?.single()?.tools?.size)
	}
	
	// endregion
	
	// region 中断
	
	@Test
	fun `shouldBreak stops processing without executing`() = runTest {
		val ctx = manager()
		val tool = toolMock()
		val processor = processor(ctx, tool, shouldBreak = true)
		
		val reasons = processor.process(model)
		
		assertTrue(reasons.isEmpty())
		coVerify(exactly = 0) { tool.execute(any(), any(), any(), any()) }
	}
	
	// endregion
}
