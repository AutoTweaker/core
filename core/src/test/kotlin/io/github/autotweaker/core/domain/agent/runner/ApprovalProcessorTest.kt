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

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@Suppress("UNCHECKED_CAST")
class ApprovalProcessorTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val model = mockk<AgentModel>()
	
	private fun manager() = AgentContextManager(
		initial = RuntimeContext(null, null, null, null, null),
	).also { manager ->
		runBlocking {
			manager.beginRound(
				RuntimeContext.Message.User(
					id = UUID.randomUUID(),
					content = MessageContent(content = "hello"),
					timestamp = Clock.System.now(),
				)
			)
			manager.applyThinking(assistant(), listOf(pendingCall("c1"), pendingCall("c2")), emptyList())
		}
	}
	
	private fun assistant() = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = "calling tools",
		modelId = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		usage = null,
	)
	
	private fun pendingCall(callId: String) = RuntimeContext.CurrentRound.PendingToolCall(
		id = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		callId = callId,
		callName = "bash-run",
		arguments = """{"cmd":"echo"}""",
		reason = "because",
		validatedToolName = "bash",
		validatedArgs = JsonPrimitive("{}"),
		resolvedRequest = JsonPrimitive("{}"),
		presentation = listOf(UiBlock.Text("请求执行命令")),
	)
	
	private fun resolvedCall(callId: String) = pendingCall(callId) to Tool.ResolveResult.Ready(
		result = JsonPrimitive("{}"),
		request = { listOf(UiBlock.Text("请求执行命令")) },
		executing = { listOf(UiBlock.Text("正在执行命令")) },
		cancelled = { listOf(UiBlock.Text("执行命令被取消")) },
		rejected = { listOf(UiBlock.Text("执行命令被拒绝")) },
		failed = { listOf(UiBlock.Text("执行命令失败")) },
		timeout = { listOf(UiBlock.Text("执行命令超时")) },
	)
	
	private fun toolResult(content: String = "tool done") = RuntimeContext.Message.Tool.Result(
		id = UUID.randomUUID(),
		content = content,
		data = null,
		presentation = listOf(UiBlock.Text("执行了命令")),
		timestamp = Clock.System.now(),
		status = ToolResultStatus.SUCCESS,
	)
	
	// region 批准
	
	@Test
	fun `approved call executes tool and returns reason`() = runTest {
		val ctx = manager()
		val tool = mockk<ToolCallingStage>()
		coEvery { tool.execute(any(), any(), any(), any()) } returns toolResult()
		val processor = ApprovalProcessor(ctx, tool, this, MutableStateFlow(false))
		
		processor.approvalChannel.send(ToolApprove("c1", reason = "go ahead"))
		val reasons = processor.process(listOf(resolvedCall("c1")), model, MutableStateFlow(AgentStatus.FREE))
		
		assertEquals(listOf("go ahead"), reasons)
		coVerify(exactly = 1) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		val tools = ctx.context.value.currentRound?.turns?.single()?.tools
		assertEquals(1, tools?.size)
		assertEquals(ToolResultStatus.SUCCESS, tools!![0].result.status)
		assertEquals("tool done", tools[0].result.content)
	}
	
	@Test
	fun `approved call without reason returns empty reasons`() = runTest {
		val ctx = manager()
		val tool = mockk<ToolCallingStage>()
		coEvery { tool.execute(any(), any(), any(), any()) } returns toolResult()
		val processor = ApprovalProcessor(ctx, tool, this, MutableStateFlow(false))
		
		processor.approvalChannel.send(ToolApprove("c1", reason = null))
		val reasons = processor.process(listOf(resolvedCall("c1")), model, MutableStateFlow(AgentStatus.FREE))
		
		assertTrue(reasons.isEmpty())
	}
	
	// endregion
	
	// region 拒绝
	
	@Test
	fun `rejected call records rejected result without executing`() = runTest {
		val ctx = manager()
		val tool = mockk<ToolCallingStage>()
		coEvery { tool.execute(any(), any(), any(), any()) } returns toolResult()
		val processor = ApprovalProcessor(ctx, tool, this, MutableStateFlow(false))
		
		processor.approvalChannel.send(ToolApprove("c1", reason = "no thanks", approved = false))
		val reasons = processor.process(listOf(resolvedCall("c1")), model, MutableStateFlow(AgentStatus.FREE))
		
		assertTrue(reasons.isEmpty())
		coVerify(exactly = 0) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		val result = ctx.context.value.currentRound?.turns?.single()?.tools?.single()?.result
		assertEquals(ToolResultStatus.REJECTED, result?.status)
		assertTrue(result!!.content.contains("no thanks"))
	}
	
	// endregion
	
	// region 乱序
	
	@Test
	fun `out-of-order approvals are stashed until their turn`() = runTest {
		val ctx = manager()
		val tool = mockk<ToolCallingStage>()
		coEvery { tool.execute(any(), any(), any(), any()) } returns toolResult()
		val processor = ApprovalProcessor(ctx, tool, this, MutableStateFlow(false))
		
		processor.approvalChannel.send(ToolApprove("c2", reason = "second first"))
		processor.approvalChannel.send(ToolApprove("c1", reason = "first"))
		val reasons = processor.process(
			listOf(resolvedCall("c1"), resolvedCall("c2")),
			model,
			MutableStateFlow(AgentStatus.FREE),
		)
		
		assertEquals(listOf("first", "second first"), reasons)
		coVerify(exactly = 2) { tool.execute(any(), any(), any(), any()) }
		
		ctx.finalizeToolTurn()
		assertEquals(2, ctx.context.value.currentRound?.turns?.single()?.tools?.size)
	}
	
	// endregion
	
	// region 中断
	
	@Test
	fun `shouldBreak stops processing without executing`() = runTest {
		val ctx = manager()
		val tool = mockk<ToolCallingStage>()
		coEvery { tool.execute(any(), any(), any(), any()) } returns toolResult()
		val processor = ApprovalProcessor(ctx, tool, this, MutableStateFlow(true))
		
		val reasons = processor.process(
			listOf(resolvedCall("c1"), resolvedCall("c2")),
			model,
			MutableStateFlow(AgentStatus.FREE),
		)
		
		assertTrue(reasons.isEmpty())
		coVerify(exactly = 0) { tool.execute(any(), any(), any(), any()) }
	}
	
	// endregion
}
