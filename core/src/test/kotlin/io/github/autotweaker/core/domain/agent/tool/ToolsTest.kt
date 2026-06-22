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

package io.github.autotweaker.core.domain.agent.tool

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import java.util.*
import kotlin.test.*

class ToolsTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val agentId = UUID.randomUUID()
	
	// region helpers
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	) : ToolArgs
	
	@Suppress("UNCHECKED_CAST")
	private fun mockTool(
		name: String = "bash",
		description: String = "a tool",
	): Tool<ToolArgs> {
		val tool = mockk<Tool<BashArgs>>()
		every { tool.name } returns name
		every { tool.description } returns description
		every { tool.argsSerializer } returns BashArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			BashArgs::cmd to "command",
			BashArgs::type to "Function type",
		)
		coEvery { tool.describeFunctions() } returns emptyMap()
		return tool as Tool<ToolArgs>
	}
	
	private fun toolCall(
		id: String = "c1",
		name: String = "bash-run",
		arguments: String = """{"cmd":"echo","reason":"test"}""",
	) = ChatMessage.AssistantMessage.ToolCall(
		id = id, name = name, arguments = arguments,
	)
	
	private fun makeTools(tools: List<Tool<ToolArgs>>, toolInfo: List<ToolInfo>) = Tools(
		toolInfo = toolInfo,
		tools = tools,
		agentId = agentId,
	)
	
	private suspend fun Tool<ToolArgs>.info() = Tools.buildToolInfo(this, true)
	private suspend fun Tool<ToolArgs>.infoInactive() = Tools.buildToolInfo(this, false)
	
	// endregion
	
	// region resolveToolCall
	
	@Test
	fun `resolveToolCall inactive tool returns Activation`() = runTest {
		val tool = mockTool()
		val inactiveInfo = tool.infoInactive()
		val tools = makeTools(listOf(tool), listOf(inactiveInfo))
		
		assertFalse(tools.toolInfo.value[0].active)
		val result = tools.resolveToolCall(toolCall(name = "bash"))
		
		assertIs<ToolCallResolveResult.Activation>(result)
		assertFalse(tools.toolInfo.value[0].active)
	}
	
	@Test
	fun `resolveToolCall active tool returns NeedsApproval`() = runTest {
		val tool = mockTool()
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val result = tools.resolveToolCall(toolCall(name = "bash-run"))
		
		assertIs<ToolCallResolveResult.NeedsApproval>(result)
	}
	
	@Test
	fun `resolveToolCall unknown tool returns ParseFailure`() = runTest {
		val tool = mockTool("inactive")
		val tools = makeTools(listOf(tool), listOf(tool.infoInactive()))
		val result = tools.resolveToolCall(toolCall(name = "unknown-run"))
		
		assertIs<ToolCallResolveResult.ParseFailure>(result)
	}
	// endregion
	
	// region executeTool
	
	@Test
	fun `executeTool runs active tool successfully`() = runTest {
		val tool = mockTool()
		coEvery { (tool as Tool<BashArgs>).execute(any(), any()) } returns Tool.ToolOutput("output ok", true)
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val result = tools.executeTool("bash", "c2", BashArgs(cmd = "echo"), SimpleContainer()) {}
		
		assertEquals(ToolResultStatus.SUCCESS, result.status)
		assertEquals("output ok", result.content)
	}
	
	@Test
	fun `executeTool runs active tool with failure`() = runTest {
		val tool = mockTool()
		coEvery { (tool as Tool<BashArgs>).execute(any(), any()) } returns Tool.ToolOutput("error happened", false)
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val result = tools.executeTool("bash", "c2", BashArgs(cmd = "echo"), SimpleContainer()) {}
		
		assertEquals(ToolResultStatus.FAILURE, result.status)
		assertEquals("error happened", result.content)
	}
	
	@Test
	fun `executeTool handles exception from tool`() = runTest {
		val tool = mockTool()
		coEvery { (tool as Tool<BashArgs>).execute(any(), any()) } throws RuntimeException("crash!")
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val result = tools.executeTool("bash", "c2", BashArgs(cmd = "echo"), SimpleContainer()) {}
		
		assertEquals(ToolResultStatus.FAILURE, result.status)
		assertEquals("crash!", result.content)
	}
	
	@Test
	fun `executeTool rethrows CancellationException`() = runTest {
		val tool = mockTool()
		coEvery { (tool as Tool<BashArgs>).execute(any(), any()) } throws CancellationException("cancelled")
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		assertFailsWith<CancellationException> {
			tools.executeTool("bash", "c2", BashArgs(cmd = "echo"), SimpleContainer()) {}
		}
	}
	
	@Test
	fun `executeTool streams runtime output`() = runTest {
		val tool = mockTool()
		coEvery { (tool as Tool<BashArgs>).execute(any(), any()) } coAnswers {
			val channel = secondArg<Channel<Tool.RuntimeOutput>?>()
			channel!!.send(Tool.RuntimeOutput("progress 1", Tool.RuntimeOutput.OutputType.INFO))
			channel.send(Tool.RuntimeOutput("progress 2", Tool.RuntimeOutput.OutputType.INFO))
			Tool.ToolOutput("done", true)
		}
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val outputs = mutableListOf<String>()
		val result = tools.executeTool(
			"bash", "c2", BashArgs(cmd = "echo"), SimpleContainer(),
			onToolOutput = { outputs.add((it as AgentOutput.Tool).output.content) },
		)
		
		assertEquals(ToolResultStatus.SUCCESS, result.status)
		assertEquals("done", result.content)
		assertEquals(listOf("progress 1", "progress 2"), outputs)
	}
	// endregion
	
	// region assembleTools
	
	@Test
	fun `assembleTools returns null when no tools`() = runTest {
		val tools = makeTools(emptyList(), emptyList())
		val result = tools.assembleTools()
		assertNull(result)
	}
	
	@Test
	fun `assembleTools with only inactive tools`() = runTest {
		val bash = mockTool("bash", "a bash tool")
		val read = mockTool("read", "a read tool")
		val info = listOf(bash.infoInactive(), read.infoInactive())
		val tools = makeTools(listOf(bash, read), info)
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(2, result.size)
		result.forEach { tool ->
			val params = tool.parameters
			val props = params.jsonObject["properties"]?.jsonObject
			assertNotNull(props)
			assertTrue(props.containsKey("enable"))
		}
	}
	
	@Test
	fun `assembleTools with only active tools`() = runTest {
		val tool = mockTool("bash", "bash tool")
		val tools = makeTools(listOf(tool), listOf(tool.info()))
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(1, result.size)
		assertEquals("bash-run", result[0].name)
	}
	
	@Test
	fun `assembleTools with both active and inactive tools`() = runTest {
		val activeTool = mockTool("bash", "bash tool")
		val inactiveTool = mockTool("read", "read tool")
		val info = listOf(activeTool.info(), inactiveTool.infoInactive())
		val tools = makeTools(listOf(activeTool, inactiveTool), info)
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(2, result.size)
		assertTrue(result.any { it.name == "bash-run" })
		assertTrue(result.any { it.name == "read" })
	}
	// endregion
	
	// region activate/deactivate
	
	@Test
	fun `activate toggles tool active state`() = runTest {
		val tool = mockTool()
		val tools = makeTools(listOf(tool), listOf(tool.infoInactive()))
		
		assertFalse(tools.toolInfo.value[0].active)
		
		tools.activate("bash", true)
		assertTrue(tools.toolInfo.value[0].active)
		
		tools.activate("bash", false)
		assertFalse(tools.toolInfo.value[0].active)
	}
	
	@Test
	fun `activate unknown tool does nothing`() = runTest {
		val tool = mockTool()
		val tools = makeTools(listOf(tool), listOf(tool.infoInactive()))
		tools.activate("nonexistent", true)
		
		assertFalse(tools.toolInfo.value[0].active)
	}
	// endregion
}
