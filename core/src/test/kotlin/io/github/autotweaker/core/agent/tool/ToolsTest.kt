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

package io.github.autotweaker.core.agent.tool

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.tool.ToolCallValidator.ValidationResult
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.session.workspace.Workspace
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.Tool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.*
import kotlin.time.Clock

class ToolsTest {
	
	private val enableDesc = "Set true to enable this tool"
	private val enabledMsg = "Tool %s with %d functions enabled"
	
	private val defaultSettings: List<SettingItem> = listOf(
		SettingItem(SettingKey("core.agent.tool.description.reason"), SettingItem.Value.ValString("reason"), ""),
		SettingItem(SettingKey("core.agent.tool.description.enable"), SettingItem.Value.ValString(enableDesc), ""),
		SettingItem(SettingKey("core.agent.tool.response.active"), SettingItem.Value.ValString(enabledMsg), ""),
		SettingItem(SettingKey("core.agent.tool.response.json.error"), SettingItem.Value.ValString("JSON: %s"), ""),
		SettingItem(
			SettingKey("core.agent.tool.response.property.missing"),
			SettingItem.Value.ValString("missing: %s %s"),
			""
		),
		SettingItem(
			SettingKey("core.agent.tool.response.property.error"),
			SettingItem.Value.ValString("error: %s %s %s"),
			""
		),
		SettingItem(
			SettingKey("core.agent.tool.response.function.name.error"),
			SettingItem.Value.ValString("name: %s"),
			""
		),
	)
	
	private val mockModel: Model = mockk(relaxed = true)
	
	// region helpers
	
	private fun mockTool(
		name: String = "bash",
		description: String = "a tool",
		functions: List<Tool.Function> = listOf(
			Tool.Function(
				"run", "run something", mapOf(
					"cmd" to Tool.Function.Property("command", true, Tool.Function.Property.ValueType.StringValue()),
				)
			)
		),
	): Tool {
		val tool = mockk<Tool>()
		val meta = Tool.Meta(name, description, functions)
		every { tool.resolveMeta(any()) } returns meta
		return tool
	}
	
	private fun pendingToolCall(
		callId: String = "c1",
		name: String = "bash_run",
	) = AgentContext.CurrentRound.PendingToolCall(
		callId = callId, name = name, model = mockModel,
		arguments = """{"cmd":"echo","reason":"test"}""",
		reason = "test", timestamp = Clock.System.now(),
	)
	
	private fun validationSuccess(
		toolName: String = "bash",
		functionName: String = "run",
	) = ValidationResult.Success(
		toolName = toolName, functionName = functionName,
		reason = "test", arguments = buildJsonObject { put("cmd", "echo") },
	)
	// endregion
	
	// region basic
	
	@Test
	fun `add tool increases entries`() {
		val tools = Tools(defaultSettings)
		assertEquals(0, tools.entries.size)
		
		tools.add(mockTool())
		assertEquals(1, tools.entries.size)
		assertFalse(tools.entries[0].active)
	}
	
	@Test
	fun `add multiple tools`() {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("bash"))
		tools.add(mockTool("read"))
		
		assertEquals(2, tools.entries.size)
	}
	// endregion
	
	// region resolveToolCalls
	
	@Test
	fun `resolveToolCalls all parse failures`() {
		val tools = Tools(defaultSettings)
		
		// No active tools -> validator has empty tool list -> all will fail
		tools.add(mockTool())
		val calls = listOf(pendingToolCall("c1"), pendingToolCall("c2"))
		
		val results = tools.resolveToolCalls(calls)
		
		assertEquals(2, results.size)
		results.forEach { assertIs<Tools.ToolCallResolveResult.ParseFailure>(it) }
	}
	
	@Test
	fun `resolveToolCalls all needs approval`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		tools.add(tool)
		
		// Activate the tool first so validator sees it
		val activateResult = tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, activateResult.result.status)
		
		val calls = listOf(pendingToolCall("c2", "bash_run"))
		val results = tools.resolveToolCalls(calls)
		
		assertEquals(1, results.size)
		assertIs<Tools.ToolCallResolveResult.NeedsApproval>(results[0])
	}
	
	@Test
	fun `resolveToolCalls inactive tool name fails validation`() {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("inactive"))
		
		val calls = listOf(pendingToolCall("c1", "inactive_run"))
		val results = tools.resolveToolCalls(calls)
		
		assertEquals(1, results.size)
		assertIs<Tools.ToolCallResolveResult.ParseFailure>(results[0])
	}
	// endregion
	
	// region executeTool
	
	@Test
	fun `executeTool activates inactive tool`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool(
			"bash", "bash tool", listOf(
				Tool.Function(
					"run", "run cmd", mapOf(
						"cmd" to Tool.Function.Property("cmd", true, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		tools.add(tool)
		
		val result = tools.executeTool(
			validationSuccess("bash", "run"),
			pendingToolCall("c1", "bash_run"),
			SimpleContainer(),
			Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		assertTrue(tools.entries[0].active)
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		assertTrue(result.result.content.contains("bash"))
	}
	
	@Test
	fun `executeTool runs active tool successfully`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } returns Tool.ToolOutput("output ok", true)
		tools.add(tool)
		
		// First call: activate
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		// Second call: execute
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		assertEquals("output ok", result.result.content)
		coVerify { tool.execute(any()) }
	}
	
	@Test
	fun `executeTool runs active tool with failure`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } returns Tool.ToolOutput("error happened", false)
		tools.add(tool)
		
		// Activate
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		// Execute
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.FAILURE, result.result.status)
		assertEquals("error happened", result.result.content)
	}
	
	@Test
	fun `executeTool handles exception from tool`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } throws RuntimeException("crash!")
		tools.add(tool)
		
		// Activate
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		// Execute - should catch exception and return failure
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.FAILURE, result.result.status)
		assertEquals("crash!", result.result.content)
	}
	
	@Test
	fun `executeTool rethrows CancellationException`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } throws CancellationException("cancelled")
		tools.add(tool)
		
		// Activate
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		assertFailsWith<CancellationException> {
			tools.executeTool(
				validationSuccess(), pendingToolCall("c2", "bash_run"),
				SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
			)
		}
	}
	
	@Test
	fun `executeTool streams runtime output`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } coAnswers {
			val input = firstArg<Tool.ToolInput>()
			input.outputChannel!!.send(Tool.RuntimeOutput("progress 1"))
			input.outputChannel.send(Tool.RuntimeOutput("progress 2"))
			Tool.ToolOutput("done", true)
		}
		tools.add(tool)
		
		// Activate
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		val outputs = mutableListOf<String>()
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
			onToolOutput = { outputs.add(it.content) },
		)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		assertEquals("done", result.result.content)
		assertEquals(listOf("progress 1", "progress 2"), outputs)
	}
	
	@Test
	fun `executeTool calls onToolActivated when tool activated`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any()) } returns Tool.ToolOutput("ok", true)
		tools.add(tool)
		
		val activatedTools = mutableListOf<List<Tool>>()
		tools.executeTool(
			validationSuccess(), pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
			onToolActivated = { activatedTools.add(it) },
		)
		
		assertEquals(1, activatedTools.size)
		assertTrue(activatedTools[0].contains(tool))
	}
	// endregion
	
	// region assembleTools
	
	@Test
	fun `assembleTools returns null when no tools`() {
		val tools = Tools(defaultSettings)
		val result = tools.assembleTools()
		assertNull(result)
	}
	
	@Test
	fun `assembleTools with only inactive tools`() {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("bash", "a bash tool"))
		tools.add(mockTool("read", "a read tool"))
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(2, result.size)
		// All should have enabled boolean schema
		result.forEach { tool ->
			val params = tool.parameters
			val props = params.jsonObject["properties"]?.jsonObject
			assertNotNull(props)
			assertTrue(props.containsKey("enable"))
		}
	}
	
	@Test
	fun `assembleTools with only active tools`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool(
			"bash", "bash tool", listOf(
				Tool.Function(
					"run", "run cmd", mapOf(
						"cmd" to Tool.Function.Property("cmd", true, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		tools.add(tool)
		
		// Activate
		tools.executeTool(
			validationSuccess("bash", "run"),
			pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(1, result.size)
		assertEquals("bash_run", result[0].name)
	}
	
	@Test
	fun `assembleTools with both active and inactive tools`() = runTest {
		val tools = Tools(defaultSettings)
		val activeTool = mockTool(
			"bash", "bash tool", listOf(
				Tool.Function(
					"run", "run cmd", mapOf(
						"cmd" to Tool.Function.Property("cmd", true, Tool.Function.Property.ValueType.StringValue()),
					)
				)
			)
		)
		val inactiveTool = mockTool("read", "read tool")
		tools.add(activeTool)
		tools.add(inactiveTool)
		
		// Activate bash only
		tools.executeTool(
			validationSuccess("bash", "run"),
			pendingToolCall("c1", "bash_run"),
			SimpleContainer(), Workspace("test", false, kotlin.io.path.createTempDirectory("test")),
		)
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(2, result.size)
		assertTrue(result.any { it.name == "bash_run" })
		assertTrue(result.any { it.name == "read" })
	}
	// endregion
}
