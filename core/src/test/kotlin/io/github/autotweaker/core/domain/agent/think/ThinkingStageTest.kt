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

package io.github.autotweaker.core.domain.agent.think

import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.agent.compact.SummaryService
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.port.TemporaryStorage
import io.github.autotweaker.core.domain.tool.port.TruncationService
import io.github.autotweaker.core.test.TestServices
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.util.*
import kotlin.test.*

@Suppress("UNCHECKED_CAST")
class ThinkingStageTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val msg = TestServices.messageBuilder()
	
	private val provider = ToolProvider(
		shellExecutor = mockk<ShellExecutor>(),
		rawFileSystem = mockk<RawFileSystem>(),
		pathResolver = mockk<PathResolver>(),
		temporaryStorage = mockk<TemporaryStorage>(),
		summaryService = mockk<SummaryService>(),
	)
	
	private val workspace: () -> Path = { Path.of(".") }
	private val truncation = mockk<TruncationService>()
	private val model = AgentModel(
		model = mockk<RuntimeModel>(),
		reasoning = null,
		summarize = mockk<RuntimeModel>(),
		compact = mockk<RuntimeModel>(),
		fallback = null,
	)
	private val context = RuntimeContext(null, null, null, null, null)
	
	private suspend fun assistant() = msg.assistant(
		reasoning = null,
		content = "I will call a tool",
		model = UUID.randomUUID(),
		usage = null,
	)
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	) : ToolArgs
	
	private fun presentation(text: String = "tool call") = listOf(UiBlock.Text(text))
	
	private fun ready(result: JsonElement = JsonPrimitive("{}")) = Tool.ResolveResult.Ready(
		result = result,
		request = { presentation("requested command") },
		executing = { presentation("executing command") },
		cancelled = { presentation("cancelled command") },
		rejected = { presentation("rejected command") },
		failed = { presentation("failed command") },
		timeout = { presentation("timed out command") },
	)
	
	private fun rejected(reason: String) = Tool.ResolveResult.Rejected(
		reason, presentation("rejected command")
	)
	
	private fun mockTool(
		name: String = "bash",
		resolve: Tool.ResolveResult = ready()
	): Tool<ToolArgs> {
		val tool = mockk<Tool<BashArgs>>()
		coEvery { tool.meta() } returns Pair(
			ToolMeta(
				name, "a tool", listOf(
					ToolMeta.Function(
						"run", "run a command", listOf(
							ToolMeta.Prop("cmd", ToolMeta.Type.TString, true, "command"),
							ToolMeta.Prop("type", ToolMeta.Type.TString, true, "Function type"),
						)
					)
				)
			),
			BashArgs.serializer()
		)
		coEvery { tool.resolve(any(), any()) } returns resolve
		return tool as Tool<ToolArgs>
	}
	
	private suspend fun makeTools(
		tools: List<Tool<ToolArgs>>,
		activeToolNames: Set<String>,
	): Tools {
		val toolMap = tools.associate { it.meta().first.name to it }
		return Tools(
			workspace = workspace,
			tools = toolMap,
			activeTools = activeToolNames,
			agentId = UUID.randomUUID(),
			msg = msg,
		).also { it.assembleTools() }
	}
	
	private fun llmService(result: LlmService.CallResult?): LlmService {
		val service = mockk<LlmService>()
		coEvery { service.execute(model, any(), any()) } returns result
		return service
	}
	
	private fun call(
		id: String,
		name: String = "bash-run",
		arguments: String = """{"cmd":"echo","reason":"tests"}""",
	) = ChatMessage.Assistant.ToolCall(id = id, name = name, arguments = arguments)
	
	private fun stage(llm: LlmService, tools: Tools) = ThinkingStage(
		llmService = llm,
		tools = tools,
		provider = provider,
		workspace = workspace,
		truncation = truncation,
		status = MutableStateFlow(AgentStatus.FREE),
		onOutput = {},
	)
	
	private suspend fun success(toolCalls: List<ChatMessage.Assistant.ToolCall>?) =
		success(toolCalls, assistant())
	
	private fun success(
		toolCalls: List<ChatMessage.Assistant.ToolCall>?,
		assistantMessage: AgentMessage.Assistant,
	) = LlmService.CallResult(
		assistantMessage = assistantMessage,
		toolCalls = toolCalls,
	)
	
	private fun ThinkingStage.Result.resolved() = toolCalls!!.single().second
	
	// region 无工具调用
	
	@Test
	fun `llm failure returns null`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val result = stage(llmService(null), tools)
			.execute(model, emptyList(), context)
		
		assertNull(result)
	}
	
	@Test
	fun `success without tool calls returns Result without tool calls`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val expected = assistant()
		val result = stage(llmService(success(null, expected)), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(expected, result.assistantMessage)
		assertNull(result.toolCalls)
	}
	
	@Test
	fun `success with empty tool call list returns Result`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val result = stage(llmService(success(emptyList())), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertNull(result.toolCalls)
	}
	
	// endregion
	
	// region 单调用分流
	
	@Test
	fun `activating an inactive tool returns Activation`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), emptySet())
		val rawCall = call(
			"c1",
			name = "active",
			arguments = """{"tool_name":"bash","reason":"activate the bash tool"}"""
		)
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val activation = result.resolved() as ResolveResult.Activation
		assertEquals(rawCall, result.toolCalls!!.single().first)
		assertEquals("bash", activation.targetName)
		assertTrue(activation.message.contains("工具已激活"))
	}
	
	@Test
	fun `invalid arguments become ParseFailure`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1", arguments = """{"cmd":"echo"}""")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val failure = result.resolved() as ResolveResult.ParseFailure
		assertEquals(rawCall, result.toolCalls!!.single().first)
		assertTrue(failure.errorMessage.contains("reason"))
	}
	
	@Test
	fun `rejected resolve becomes ResolveFailure`() = runTest {
		val tool = mockTool("bash", rejected("文件test.txt不存在或访问被拒绝"))
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val failure = result.resolved() as ResolveResult.ResolveFailure
		assertEquals(rawCall, result.toolCalls!!.single().first)
		assertEquals("tests", failure.reason)
		assertEquals("bash", failure.toolName)
		assertEquals("文件test.txt不存在或访问被拒绝", failure.errorMessage)
		assertNotNull(failure.validatedArgs)
	}
	
	@Test
	fun `resolve exception becomes ResolveFailure with error message`() = runTest {
		val tool = mockTool("bash")
		coEvery { tool.resolve(any(), any()) } throws RuntimeException("boom")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val result = stage(llmService(success(listOf(call("c1")))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val failure = result.resolved() as ResolveResult.ResolveFailure
		assertTrue(failure.errorMessage.contains("调用参数在解析时出错"))
		assertTrue(failure.errorMessage.contains("RuntimeException: boom"))
	}
	
	@Test
	fun `ready resolve becomes NeedsApproval`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val approval = result.resolved() as ResolveResult.NeedsApproval
		assertEquals(rawCall, result.toolCalls!!.single().first)
		assertEquals("bash", approval.toolName)
		assertEquals("tests", approval.reason)
		assertEquals(JsonPrimitive("{}"), approval.resolveResult.result)
	}
	
	// endregion
	
	// region 多调用混合
	
	@Test
	fun `mixed calls keep their order and results`() = runTest {
		val bash = mockTool("bash")
		val read = mockTool("read", rejected("文件不存在"))
		val edit = mockTool("edit")
		val tools = makeTools(listOf(bash, read, edit), setOf("bash", "read"))
		
		val activationCall = call(
			"c1", name = "active", arguments = """{"tool_name":"edit","reason":"activate the edit tool"}"""
		)
		val parseCall = call("c2", arguments = """{"cmd":"echo"}""")
		val pendingCall = call("c3")
		val rejectCall = call("c4", name = "read-run")
		
		val result = stage(
			llmService(success(listOf(activationCall, parseCall, pendingCall, rejectCall))),
			tools
		).execute(model, emptyList(), context)
		
		assertNotNull(result)
		val calls = result.toolCalls!!
		assertEquals(listOf("c1", "c2", "c3", "c4"), calls.map { it.first.id })
		assertIs<ResolveResult.Activation>(calls[0].second)
		assertIs<ResolveResult.ParseFailure>(calls[1].second)
		assertIs<ResolveResult.NeedsApproval>(calls[2].second)
		assertIs<ResolveResult.ResolveFailure>(calls[3].second)
		assertEquals("read", (calls[3].second as ResolveResult.ResolveFailure).toolName)
	}
	
	// endregion
}
