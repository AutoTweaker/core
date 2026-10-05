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
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.chat.MessageConverts
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.test.TestServices
import io.mockk.coEvery
import io.mockk.coVerify
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
	private val msg = TestServices.messageBuilder(agentId)
	
	private data class Harness(
		val ctx: ContextManager,
		val runner: RoundRunner,
		val status: MutableStateFlow<AgentStatus>,
	)
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	) : ToolArgs
	
	private fun presentation(text: String = "tool call") = listOf(UiBlock.Text(text))
	
	private fun rawCall(id: String = "c1", name: String = "bash-run") =
		ChatMessage.Assistant.ToolCall(id = id, name = name, arguments = """{"cmd":"echo"}""")
	
	private fun ready() = Tool.ResolveResult.Ready(
		result = JsonPrimitive("{}"),
		request = { presentation("requested command") },
		executing = { presentation("executing command") },
		cancelled = { presentation("cancelled command") },
		rejected = { presentation("rejected command") },
		failed = { presentation("failed command") },
		timeout = { presentation("timed out command") },
	)
	
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
			msg = msg,
		).also { it.assembleTools() }
	}
	
	private suspend fun assistant(content: String? = "ok") =
		msg.assistant(reasoning = null, content = content, model = UUID.randomUUID(), usage = null)
	
	private suspend fun done(content: String? = "ok") = ThinkingStage.Result(assistant(content), null)
	
	private suspend fun hasPending(callId: String = "c1") = ThinkingStage.Result(
		assistant("calling"),
		listOf(
			rawCall(callId) to ResolveResult.NeedsApproval(
				toolName = "bash",
				reason = "because",
				validatedArgs = JsonPrimitive("{}"),
				resolveResult = ready(),
			)
		)
	)
	
	private suspend fun activated(call: ChatMessage.Assistant.ToolCall, toolName: String) = ThinkingStage.Result(
		assistant("activate"),
		listOf(
			call to ResolveResult.Activation(
				targetName = toolName,
				reason = "activate me",
				validatedArgs = JsonPrimitive("""{"tool_name":"bash"}"""),
				presentation = presentation("activated tool"),
				message = "activate me",
			)
		)
	)
	
	private suspend fun parseFailure(call: ChatMessage.Assistant.ToolCall) = ThinkingStage.Result(
		assistant("bad call"),
		listOf(
			call to ResolveResult.ParseFailure(
				errorMessage = "missing reason",
				presentation = presentation("failed to call bash"),
			)
		)
	)
	
	private fun harness(
		tools: Tools,
		thinking: ThinkingStage,
	): Harness {
		val ctx = ContextManager(RuntimeContext(null, null, null, null, null), msg)
		val status = MutableStateFlow(AgentStatus.FREE)
		val toolCalling = mockk<ToolCallingStage>()
		coEvery { toolCalling.cancelToolJob() } returns Unit
		coEvery { toolCalling.execute(any(), any(), any(), any()) } coAnswers {
			val call = firstArg<AgentToolCallImpl>()
			call.finish(
				msg.toolResult(
					callId = call.call.callId,
					content = "tool result",
					data = null,
					presentation = presentation("executed command"),
					status = ToolResultStatus.SUCCESS,
				)
			)
		}
		val compact = mockk<CompactService>()
		coEvery { compact.execute(any(), any()) } returns Unit
		val runner = RoundRunner(
			agentModel = model,
			msg = msg,
			ctx = ctx,
			workspace = workspace,
			tools = tools,
			thinkingStage = thinking,
			toolCalling = toolCalling,
			compactService = compact,
			status = status,
			compacting = MutableStateFlow(false),
			agentId = agentId,
			converts = mockk<MessageConverts>(relaxed = true),
			cache = TestServices.messageCache,
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
		val answer = done("answer")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returns answer
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 && h.status.value == AgentStatus.FREE }
		
		val completed = h.ctx.context.value.historyRounds!!.single()
		assertEquals("hello\n", completed.userMessage.getOrNull()?.content?.content?.merge())
		assertEquals("answer", completed.assistantMessage?.getOrNull()?.content)
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
		h.runner.send(MessageContent(content = "hello".toContentPart())).await()
		awaitUntil { h.status.value == AgentStatus.FREE && h.ctx.context.value.currentRound == null }
		
		assertNull(h.ctx.context.value.historyRounds)
		h.runner.shutdown()
	}
	
	// endregion
	
	// region 错误分支
	
	@Test
	fun `done with parse failures retries thinking`() = runTest {
		val tools = makeTools()
		val failure = parseFailure(rawCall("c1"))
		val answer = done("answer")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(failure, answer)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		assertEquals(
			ToolResultStatus.FAILURE,
			h.ctx.context.value.historyRounds!!.single().turns!!.single().tools.single().result.getOrNull()?.status
		)
		h.runner.shutdown()
	}
	
	@Test
	fun `done with activations activates tools`() = runTest {
		val tools = makeTools("bash")
		val activationCall = ChatMessage.Assistant.ToolCall("c1", "bash", """{}""")
		val activation = activated(activationCall, "bash")
		val answer = done("answer")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(activation, answer)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { "bash" in tools.activeTools.value }
		
		assertTrue("bash" in tools.activeTools.value)
		h.runner.shutdown()
	}
	
	@Test
	fun `empty response triggers feedback injection and retries`() = runTest {
		val tools = makeTools()
		val empty = done(null)
		val answer = done("real answer")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(empty, answer)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 2 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		h.runner.shutdown()
	}
	
	// endregion
	
	// region 审批回合
	
	@Test
	fun `has pending approval executes approved tool`() = runTest {
		val tools = makeTools("bash")
		val pending = hasPending()
		val answer = done("answer")
		val thinking = mockk<ThinkingStage>()
		coEvery { thinking.execute(any(), any(), any()) } returnsMany listOf(pending, answer)
		val h = harness(tools, thinking)
		
		h.runner.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { h.status.value == AgentStatus.WAITING }
		val callId = h.ctx.toolCalls!!.second.single().call.id
		h.runner.execute(AgentCommand.ApproveTool(ToolApprove(callId, reason = null)))
		awaitUntil { h.ctx.context.value.historyRounds?.size == 1 }
		
		coVerify(exactly = 2) { thinking.execute(any(), any(), any()) }
		val turn = h.ctx.context.value.historyRounds!!.single().turns!!.single()
		assertEquals(ToolResultStatus.SUCCESS, turn.tools.single().result.getOrNull()?.status)
		assertEquals("tool result", turn.tools.single().result.getOrNull()?.content)
		h.runner.shutdown()
	}
	
	// endregion
}
