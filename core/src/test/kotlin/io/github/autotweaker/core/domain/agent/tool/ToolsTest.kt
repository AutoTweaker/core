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
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import kotlinx.coroutines.channels.Channel
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator.ValidationResult
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class ToolsTest {
	private val defaultSettings: SettingService = mockk<SettingService>().also { svc ->
		every { svc.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	
	private val mockModel: Model = mockk(relaxed = true)
	
	// region helpers
	
	@Serializable
	private data class BashArgs(
		val cmd: String = "",
		val type: String = "run",
	)
	
	private fun mockTool(
		name: String = "bash",
		description: String = "a tool",
	): Tool<BashArgs> {
		val tool = mockk<Tool<BashArgs>>()
		every { tool.name } returns name
		every { tool.description } returns description
		every { tool.argsSerializer } returns BashArgs.serializer()
		coEvery { tool.describe() } returns mapOf(
			BashArgs::cmd to "command",
			BashArgs::type to "Function type",
		)
		coEvery { tool.describeFunctions() } returns emptyMap()
		return tool
	}
	
	private fun pendingToolCall(
		callId: String = "c1",
		name: String = "bash-run",
	) = AgentContext.CurrentRound.PendingToolCall(
		callId = callId, assistantMessageId = UUID.randomUUID(), name = name, modelId = mockModel.id,
		arguments = """{"cmd":"echo","reason":"test"}""",
		reason = "test", timestamp = Clock.System.now(),
	)
	
	private fun validationSuccess(
		toolName: String = "bash",
		functionName: String = "run",
	) = ValidationResult.Success(
		toolName = toolName, functionName = functionName,
		reason = "test", args = BashArgs(cmd = "echo"),
	)
	
	private fun settingsWithThreshold(threshold: Int): SettingService =
		mockk<SettingService>().also { svc ->
			every { svc.get<SettingValue>(any()) } answers {
				val def = firstArg<SettingDef<*>>()
				if (def is AgentToolSettings.DeactivationThreshold) {
					SettingValue.ValInt(threshold)
				} else {
					def.default
				}
			}
		}
	
	private suspend fun Tools.activate(toolName: String) {
		resolveToolCalls(listOf(pendingToolCall(name = toolName)))
	}
	
	// endregion
	
	// region basic
	
	@Test
	fun `add tool increases entries`() = runBlocking {
		val tools = Tools(defaultSettings)
		assertEquals(0, tools.entries.size)
		
		tools.add(mockTool())
		assertEquals(1, tools.entries.size)
		assertFalse(tools.entries[0].active)
	}
	
	@Test
	fun `add multiple tools`() = runBlocking {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("bash"))
		tools.add(mockTool("read"))
		
		assertEquals(2, tools.entries.size)
	}
	// endregion
	
	// region resolveToolCalls
	
	@Test
	fun `resolveToolCalls all parse failures`() = runBlocking {
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
		
		tools.activate("bash")
		
		val calls = listOf(pendingToolCall("c2", "bash-run"))
		val results = tools.resolveToolCalls(calls)
		
		assertEquals(1, results.size)
		assertIs<Tools.ToolCallResolveResult.NeedsApproval>(results[0])
	}
	
	@Test
	fun `resolveToolCalls inactive tool name fails validation`(): Unit = runBlocking {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("inactive"))
		
		val calls = listOf(pendingToolCall("c1", "inactive-run"))
		val results = tools.resolveToolCalls(calls)
		
		assertEquals(1, results.size)
		assertIs<Tools.ToolCallResolveResult.ParseFailure>(results[0])
	}
	// endregion
	
	// region executeTool
	
	@Test
	fun `resolveToolCalls activates inactive tool`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool("bash", "bash tool")
		tools.add(tool)
		
		val results = tools.resolveToolCalls(listOf(pendingToolCall("c1", "bash")))
		
		assertEquals(1, results.size)
		val activation = results[0] as Tools.ToolCallResolveResult.Activation
		assertTrue(tools.entries[0].active)
		assertTrue(activation.message.contains("bash-run"))
	}
	
	@Test
	fun `executeTool runs active tool successfully`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any(), any()) } returns Tool.ToolOutput("output ok", true)
		tools.add(tool)
		
		tools.activate("bash")
		
		// Execute
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		assertEquals("output ok", result.result.content)
		coVerify { tool.execute(any(), any()) }
	}
	
	@Test
	fun `executeTool runs active tool with failure`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any(), any()) } returns Tool.ToolOutput("error happened", false)
		tools.add(tool)
		
		tools.activate("bash")
		
		// Execute
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
		assertEquals("error happened", result.result.content)
	}
	
	@Test
	fun `executeTool handles exception from tool`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any(), any()) } throws RuntimeException("crash!")
		tools.add(tool)
		
		tools.activate("bash")
		
		// Execute - should catch exception and return failure
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
		assertEquals("crash!", result.result.content)
	}
	
	@Test
	fun `executeTool rethrows CancellationException`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any(), any()) } throws CancellationException("cancelled")
		tools.add(tool)
		
		tools.activate("bash")
		
		assertFailsWith<CancellationException> {
			tools.executeTool(
				validationSuccess(), pendingToolCall("c2", "bash-run"),
				SimpleContainer(), UUID.randomUUID(),
			)
		}
	}
	
	@Test
	fun `executeTool streams runtime output`() = runTest {
		val tools = Tools(defaultSettings)
		val tool = mockTool()
		coEvery { tool.execute(any(), any()) } coAnswers {
			val channel = secondArg<Channel<Tool.RuntimeOutput>?>()
			channel!!.send(Tool.RuntimeOutput("progress 1", Tool.RuntimeOutput.OutputType.INFO))
			channel.send(Tool.RuntimeOutput("progress 2", Tool.RuntimeOutput.OutputType.INFO))
			Tool.ToolOutput("done", true)
		}
		tools.add(tool)
		
		tools.activate("bash")
		
		val outputs = mutableListOf<String>()
		val result = tools.executeTool(
			validationSuccess(), pendingToolCall("c2", "bash-run"),
			SimpleContainer(), UUID.randomUUID(),
			onToolOutput = { outputs.add(it.output.content) },
		)
		
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		assertEquals("done", result.result.content)
		assertEquals(listOf("progress 1", "progress 2"), outputs)
	}
	
	@Test
	fun `executeTool deactivates unused tool after threshold`() = runTest {
		val svc = settingsWithThreshold(2)
		val tools = Tools(svc)
		val toolA = mockTool("a")
		val toolB = mockTool("b")
		coEvery { toolA.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		coEvery { toolB.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		tools.add(toolA)
		tools.add(toolB)
		
		tools.activate("a")
		tools.activate("b")
		
		assertTrue(tools.entries[0].active)
		assertTrue(tools.entries[1].active)
		
		repeat(3) {
			tools.executeTool(
				validationSuccess("a"), pendingToolCall("c${it + 3}", "a-run"),
				SimpleContainer(), UUID.randomUUID(),
			)
		}
		
		assertTrue(tools.entries[0].active)
		assertFalse(tools.entries[1].active)
	}
	
	@Test
	fun `executeTool does not deactivate when threshold is zero`() = runTest {
		val svc = settingsWithThreshold(0)
		val tools = Tools(svc)
		val toolA = mockTool("a")
		val toolB = mockTool("b")
		coEvery { toolA.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		coEvery { toolB.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		tools.add(toolA)
		tools.add(toolB)
		
		tools.activate("a")
		tools.activate("b")
		
		repeat(100) {
			tools.executeTool(
				validationSuccess("a"), pendingToolCall("c${it + 3}", "a-run"),
				SimpleContainer(), UUID.randomUUID(),
			)
		}
		
		assertTrue(tools.entries[0].active)
		assertTrue(tools.entries[1].active)
	}
	
	@Test
	fun `executeTool calls onToolDeactivated when tool deactivated`() = runTest {
		val svc = settingsWithThreshold(1)
		val tools = Tools(svc)
		val toolA = mockTool("a")
		val toolB = mockTool("b")
		coEvery { toolA.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		coEvery { toolB.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		tools.add(toolA)
		tools.add(toolB)
		
		tools.activate("a")
		tools.activate("b")
		
		tools.executeTool(
			validationSuccess("a"), pendingToolCall("c3", "a-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		val deactivatedCalls = mutableListOf<List<Tool<*>>>()
		tools.executeTool(
			validationSuccess("a"), pendingToolCall("c4", "a-run"),
			SimpleContainer(), UUID.randomUUID(),
			onToolDeactivated = { deactivatedCalls.add(it) },
		)
		
		assertEquals(1, deactivatedCalls.size)
		assertTrue(deactivatedCalls[0].contains(toolA))
		assertFalse(deactivatedCalls[0].contains(toolB))
	}
	
	@Test
	fun `executeTool resets called tool counter and increments others`() = runTest {
		val svc = settingsWithThreshold(5)
		val tools = Tools(svc)
		val toolA = mockTool("a")
		val toolB = mockTool("b")
		coEvery { toolA.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		coEvery { toolB.execute(any(), any()) } returns Tool.ToolOutput("ok", true)
		tools.add(toolA)
		tools.add(toolB)
		
		tools.activate("a")
		tools.activate("b")
		
		tools.executeTool(
			validationSuccess("a"), pendingToolCall("c3", "a-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		assertEquals(0, tools.entries[0].consecutiveUnused.get())
		assertEquals(1, tools.entries[1].consecutiveUnused.get())
		
		tools.executeTool(
			validationSuccess("b"), pendingToolCall("c4", "b-run"),
			SimpleContainer(), UUID.randomUUID(),
		)
		assertEquals(1, tools.entries[0].consecutiveUnused.get())
		assertEquals(0, tools.entries[1].consecutiveUnused.get())
	}
	// endregion
	
	// region assembleTools
	
	@Test
	fun `assembleTools returns null when no tools`() = runBlocking {
		val tools = Tools(defaultSettings)
		val result = tools.assembleTools()
		assertNull(result)
	}
	
	@Test
	fun `assembleTools with only inactive tools`() = runBlocking {
		val tools = Tools(defaultSettings)
		tools.add(mockTool("bash", "a bash tool"))
		tools.add(mockTool("read", "a read tool"))
		
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
		val tools = Tools(defaultSettings)
		val tool = mockTool("bash", "bash tool")
		tools.add(tool)
		
		tools.activate("bash")
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(1, result.size)
		assertEquals("bash-run", result[0].name)
	}
	
	@Test
	fun `assembleTools with both active and inactive tools`() = runTest {
		val tools = Tools(defaultSettings)
		val activeTool = mockTool("bash", "bash tool")
		val inactiveTool = mockTool("read", "read tool")
		tools.add(activeTool)
		tools.add(inactiveTool)
		
		tools.activate("bash")
		
		val result = tools.assembleTools()
		
		assertNotNull(result)
		assertEquals(2, result.size)
		assertTrue(result.any { it.name == "bash-run" })
		assertTrue(result.any { it.name == "read" })
	}
	// endregion
}
