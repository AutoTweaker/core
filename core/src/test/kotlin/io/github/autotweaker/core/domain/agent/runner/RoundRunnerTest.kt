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
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UNCHECKED_CAST")
class RoundRunnerTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val workspace: () -> Path = { Path.of(".") }
	private val agentId = UUID.randomUUID()
	private val model = mockk<AgentModel>()
	
	private data class Harness(
		val ctx: AgentContextManager,
		val runner: RoundRunner,
		val status: MutableStateFlow<AgentStatus>,
	)
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	) : ToolArgs
	
	private fun mockTool(name: String = "bash"): Tool<ToolArgs> {
		val tool = mockk<Tool<BashArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				name, "a tool", listOf(
					ToolMeta.Function(
						"run", "run a command", listOf(
							ToolMeta.Prop("cmd", ToolMeta.Type.TString, true, "command"),
						)
					)
				)
			),
			BashArgs.serializer()
		)
		return tool as Tool<ToolArgs>
	}
	
	private suspend fun makeTools(vararg names: String): Tools {
		val tools = names.associateWith { mockTool(it) }
		return Tools(
			workspace = workspace,
			tools = tools,
			activeTools = emptySet(),
			agentId = agentId,
		).also { it.assembleTools() }
	}
	
	private fun assistant(content: String? = "ok") = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = content,
		modelId = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		usage = null,
	)
	
	private fun done(
		content: String? = "ok",
		activations: List<Pair<ChatMessage.AssistantMessage.ToolCall, ResolveResult.Activation>>? = null,
	) = ThinkingStage.Result(assistant(content), activations, null, null, null)
	
	private fun hasPending(callId: String = "c1") = ThinkingStage.Result(
		assistantMessage = assistant("calling"),
		activations = null,
		parseFailures = null,
		resolveFailures = null,
		needsApproval = listOf(
			RuntimeContext.CurrentRound.PendingToolCall(
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
			) to Tool.ResolveResult.Ready(
				result = JsonPrimitive("{}"),
				request = { listOf(UiBlock.Text("请求执行命令")) },
				executing = { listOf(UiBlock.Text("正在执行命令")) },
				cancelled = { listOf(UiBlock.Text("执行命令被取消")) },
				rejected = { listOf(UiBlock.Text("执行命令被拒绝")) },
				failed = { listOf(UiBlock.Text("执行命令失败")) },
				timeout = { listOf(UiBlock.Text("执行命令超时")) },
			)
		),
	)
	
	private fun harness(
		tools: Tools,
		thinking: ThinkingStage,
	): Harness {
		val ctx = AgentContextManager(RuntimeContext(null, null, null, null, null))
		val status = MutableStateFlow(AgentStatus.FREE)
		val toolCalling = mockk<ToolCallingStage>()
		every { toolCalling.cancelToolJob() } returns Unit
		coEvery { toolCalling.execute(any(), any(), any(), any()) } returns RuntimeContext.Message.Tool.Result(
			id = UUID.randomUUID(),
			content = "tool result",
			data = null,
			presentation = listOf(UiBlock.Text("执行了命令")),
			timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		)
		val compact = mockk<CompactService>()
		coEvery { compact.execute(any(), any()) } returns Unit
		val runner = RoundRunner(
			ctx = ctx,
			tools = tools,
			thinkingStage = thinking,
			toolCalling = toolCalling,
			compactService = compact,
			agentModel = model,
			statusFlow = status,
			agentId = agentId,
		)
		return Harness(ctx, runner, status)
	}
	
	private suspend fun awaitUntil(condition: () -> Boolean) {
		// workLoop 跑在真实调度器上，轮询需使用真实时间
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			withTimeout(5_000.milliseconds) {
				while (!condition()) delay(10.milliseconds)
			}
		}
	}
	
	// region 干净回合
	
	@Test
	fun `clean done response completes round`() = runTest {
		val tools = makeTools()
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returns done("answer")
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello"))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 && h.status.value == AgentStatus.FREE }
		
		val completed = h.ctx.context.value.historyRounds!!.single()
		assertEquals("hello", completed.userMessage.content.content)
		assertEquals("answer", completed.finalAssistantMessage?.content)
		coVerify(exactly = 1) { thinking.execute(any(), any(), any()) }
		
		h.runner.shutdown()
	}
	
	@Test
	fun `llm failure ends round with empty history`() = runTest {
		val tools = makeTools()
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returns null
		val h = harness(tools, thinking)
		
		// 等待消息被消费，确保回合已开始；THINKING 是瞬态，回合可能在轮询开始前已完成
		h.runner.send(MessageContent(content = "hello")).await()
		awaitUntil { h.status.value == AgentStatus.FREE && h.ctx.context.value.currentRound == null }
		
		assertNull(h.ctx.context.value.historyRounds)
		h.runner.shutdown()
	}
	
	// endregion
	
	// region 错误分支
	
	@Test
	fun `done with parse failures retries thinking`() = runTest {
		val tools = makeTools()
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(
			ThinkingStage.Result(
				assistantMessage = assistant("bad call"),
				activations = null,
				parseFailures = listOf(
					ChatMessage.AssistantMessage.ToolCall("c1", "bash-run", "{}") to
							ResolveResult.ParseFailure(
								errorMessage = "missing reason",
								presentation = listOf(UiBlock.Text("调用 bash 工具失败")),
							)
				),
				resolveFailures = null,
				needsApproval = null,
			),
			done("answer"),
		)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello"))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		assertEquals(
			ToolResultStatus.FAILURE,
			h.ctx.context.value.historyRounds!!.single().turns!!.single().tools.single().result.status
		)
		h.runner.shutdown()
	}
	
	@Test
	fun `done with activations activates tools`() = runTest {
		val tools = makeTools("bash")
		val activationCall = ChatMessage.AssistantMessage.ToolCall("c1", "bash", """{}""")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(
			done(
				"activate",
				activations = listOf(
					activationCall to ResolveResult.Activation(
						message = "activate me",
						presentation = listOf(UiBlock.Text("激活了 bash 工具")),
					)
				)
			),
			done("answer"),
		)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello"))
		awaitUntil { "bash" in tools.activeTools.value }
		
		assertTrue("bash" in tools.activeTools.value)
		h.runner.shutdown()
	}
	
	@Test
	fun `empty response triggers feedback injection and retries`() = runTest {
		val tools = makeTools()
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(
			done(null),
			done("real answer"),
		)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello"))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 2 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		h.runner.shutdown()
	}
	
	// endregion
	
	// region 审批回合
	
	@Test
	fun `has pending approval executes approved tool`() = runTest {
		val tools = makeTools("bash")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(
			hasPending(),
			done("answer"),
		)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello"))
		awaitUntil { h.status.value == AgentStatus.WAITING }
		h.runner.execute(AgentCommand.ApproveTool(ToolApprove("c1", reason = null)))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		val turn = h.ctx.context.value.historyRounds!!.single().turns!!.single()
		assertEquals(ToolResultStatus.SUCCESS, turn.tools.single().result.status)
		assertEquals("tool result", turn.tools.single().result.content)
		h.runner.shutdown()
	}
	
	// endregion
}
