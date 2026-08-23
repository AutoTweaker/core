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
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.port.TemporaryStorage
import io.github.autotweaker.core.domain.tool.port.TruncationService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

@Suppress("UNCHECKED_CAST")
class ThinkingStageTest {
	companion object {
		init {
			TestServices.init()
			ToolProvider.init(
				shellExecutor = mockk<ShellExecutor>(),
				rawFileSystem = mockk<RawFileSystem>(),
				pathResolver = mockk<PathResolver>(),
				temporaryStorage = mockk<TemporaryStorage>(),
			)
		}
	}
	
	private val workspace: () -> Path = { Path.of(".") }
	private val truncation = mockk<TruncationService>()
	private val model = AgentModel(
		model = mockk<Model>(),
		reasoning = null,
		summarize = mockk<Model>(),
		compact = mockk<Model>(),
		fallback = null,
	)
	private val context = RuntimeContext(null, null, null, null, null)
	
	private val assistant = RuntimeContext.Message.Assistant(
		id = UUID.randomUUID(),
		reasoning = null,
		content = "I will call a tool",
		modelId = UUID.randomUUID(),
		timestamp = Clock.System.now(),
		usage = null,
	)
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	) : ToolArgs
	
	private fun presentation(text: String = "工具调用") = listOf(UiBlock.Text(text))
	
	private fun ready(result: JsonElement = JsonPrimitive("{}")) = Tool.ResolveResult.Ready(
		result = result,
		request = { presentation("请求执行命令") },
		executing = { presentation("正在执行命令") },
		cancelled = { presentation("执行命令被取消") },
		rejected = { presentation("执行命令被拒绝") },
		failed = { presentation("执行命令失败") },
		timeout = { presentation("执行命令超时") },
	)
	
	private fun rejected(reason: String) = Tool.ResolveResult.Rejected(
		reason, presentation("执行命令被拒绝")
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
		workspace = workspace,
		truncation = truncation,
		onOutput = {},
	)
	
	private fun success(toolCalls: List<ChatMessage.Assistant.ToolCall>?) = LlmService.CallResult(
		assistantMessage = assistant,
		toolCalls = toolCalls,
	)
	
	// region 无工具调用
	
	@Test
	fun `llm failure returns null`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val result = stage(llmService(null), tools)
			.execute(model, emptyList(), context)
		
		assertNull(result)
	}
	
	@Test
	fun `success without tool calls returns Result with empty buckets`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val result = stage(llmService(success(null)), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(assistant, result.assistantMessage)
		assertTrue(result.activations.isNullOrEmpty())
		assertTrue(result.parseFailures.isNullOrEmpty())
		assertTrue(result.resolveFailures.isNullOrEmpty())
	}
	
	@Test
	fun `success with empty tool call list returns Result`() = runTest {
		val tools = makeTools(emptyList(), emptySet())
		val result = stage(llmService(success(emptyList())), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertTrue(result.activations.isNullOrEmpty())
	}
	
	// endregion
	
	// region 单调用分流
	
	@Test
	fun `inactive tool call becomes Activation`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), emptySet())
		val result = stage(llmService(success(listOf(call("c1", name = "bash")))), tools)
			.execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.activations!!.size)
		assertEquals("c1", result.activations[0].first.id)
		assertTrue(result.activations[0].second.message.contains("工具已激活"))
		assertTrue(result.parseFailures.isNullOrEmpty())
		assertTrue(result.resolveFailures.isNullOrEmpty())
	}
	
	@Test
	fun `invalid arguments become ParseFailure`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1", arguments = """{"cmd":"echo"}""")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.parseFailures!!.size)
		assertEquals(rawCall, result.parseFailures[0].first)
		assertTrue(result.parseFailures[0].second.errorMessage.contains("reason"))
		assertTrue(result.activations.isNullOrEmpty())
		assertTrue(result.resolveFailures.isNullOrEmpty())
	}
	
	@Test
	fun `rejected resolve becomes ResolveFailure`() = runTest {
		val tool = mockTool("bash", rejected("文件test.txt不存在或访问被拒绝"))
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.resolveFailures!!.size)
		val failure = result.resolveFailures[0]
		assertEquals(rawCall, failure.first)
		assertEquals("tests", failure.second.reason)
		assertEquals("bash", failure.second.toolName)
		assertEquals("文件test.txt不存在或访问被拒绝", failure.second.errorMessage)
		assertNotNull(failure.second.validatedArgs)
		assertTrue(result.activations.isNullOrEmpty())
	}
	
	@Test
	fun `resolve exception becomes ResolveFailure with error message`() = runTest {
		val tool = mockTool("bash")
		coEvery { tool.resolve(any(), any()) } throws RuntimeException("boom")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val result = stage(llmService(success(listOf(call("c1")))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.resolveFailures!!.size)
		val failure = result.resolveFailures[0]
		assertTrue(failure.second.errorMessage.contains("调用参数在解析时出错"))
		assertTrue(failure.second.errorMessage.contains("RuntimeException: boom"))
	}
	
	@Test
	fun `ready resolve becomes needsApproval with mapped pending call`() = runTest {
		val tool = mockTool("bash")
		val tools = makeTools(listOf(tool), setOf("bash"))
		val rawCall = call("c1")
		val result = stage(llmService(success(listOf(rawCall))), tools).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.needsApproval!!.size)
		val resolved = result.needsApproval[0]
		val pending = resolved.first
		assertEquals("c1", pending.callId)
		assertEquals("bash-run", pending.callName)
		assertEquals(rawCall.arguments, pending.arguments)
		assertEquals("tests", pending.reason)
		assertEquals("bash", pending.validatedToolName)
		assertEquals(JsonPrimitive("{}"), pending.resolvedRequest)
		assertEquals(assistant.timestamp, pending.timestamp)
		assertEquals(JsonPrimitive("{}"), resolved.second.result)
	}
	
	// endregion
	
	// region 多调用混合
	
	@Test
	fun `mixed calls route to correct buckets`() = runTest {
		val bash = mockTool("bash")
		val read = mockTool("read", rejected("文件不存在"))
		val edit = mockTool("edit")
		val tools = makeTools(listOf(bash, read, edit), setOf("bash", "read"))
		
		val activationCall = call("c1", name = "edit")
		val parseCall = call("c2", arguments = """{"cmd":"echo"}""")
		val pendingCall = call("c3")
		val rejectCall = call("c4", name = "read-run")
		
		val result = stage(
			llmService(success(listOf(activationCall, parseCall, pendingCall, rejectCall))),
			tools
		).execute(model, emptyList(), context)
		
		assertNotNull(result)
		assertEquals(1, result.activations!!.size)
		assertEquals("c1", result.activations[0].first.id)
		assertEquals(1, result.parseFailures!!.size)
		assertEquals("c2", result.parseFailures[0].first.id)
		assertEquals(1, result.resolveFailures!!.size)
		assertEquals("c4", result.resolveFailures[0].first.id)
		assertEquals("read", result.resolveFailures[0].second.toolName)
		assertEquals(1, result.needsApproval!!.size)
		assertEquals("c3", result.needsApproval[0].first.callId)
	}
	
	// endregion
}
