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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.*
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.agent.tool.ToolCallValidator
import io.github.autotweaker.core.agent.tool.Tools
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.session.workspace.Workspace
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class ExecuteToolPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var tools: Tools
	private lateinit var model: Model
	private lateinit var validationResult: ToolCallValidator.ValidationResult.Success
	private lateinit var pendingCall: AgentContext.CurrentRound.PendingToolCall
	private val statusLog = mutableListOf<AgentStatus>()
	private val capturedOutputs = mutableListOf<AgentOutput>()
	private lateinit var tmpDir: Path
	private val settings = listOf(
		SettingItem(SettingKey("core.agent.tool.timeout.seconds"), SettingItem.Value.ValInt(2), ""),
		SettingItem(
			SettingKey("core.agent.tool.response.timeout"),
			SettingItem.Value.ValString("Timeout after %d seconds"),
			""
		),
		SettingItem(
			SettingKey("core.agent.tool.response.property.missing"),
			SettingItem.Value.ValString("Missing %s"),
			""
		),
		SettingItem(SettingKey("core.agent.tool.response.property.error"), SettingItem.Value.ValString("Error %s"), ""),
		SettingItem(
			SettingKey("core.agent.tool.response.function.name.error"),
			SettingItem.Value.ValString("Function %s not found"),
			""
		),
		SettingItem(SettingKey("core.agent.tool.description.enable"), SettingItem.Value.ValString("Enable tool"), ""),
		SettingItem(
			SettingKey("core.agent.tool.response.active"),
			SettingItem.Value.ValString("Tool %s with %d functions enabled"),
			""
		),
		SettingItem(
			SettingKey("core.agent.tool.response.json.error"),
			SettingItem.Value.ValString("JSON error: %s"),
			""
		),
	)
	
	@BeforeTest
	fun setUp() {
		tmpDir = Files.createTempDirectory("autotweaker-test")
		agentState = MutableAgentState()
		model = mockModel()
		tools = mockk(relaxUnitFun = true)
		statusLog.clear()
		
		validationResult = ToolCallValidator.ValidationResult.Success(
			toolName = "bash", functionName = "run", reason = "needed",
			arguments = buildJsonObject {},
		)
		pendingCall = AgentContext.CurrentRound.PendingToolCall(
			callId = "c1", assistantMessageId = UUID.randomUUID(), name = "bash_run", model = model,
			arguments = "{}", reason = "test reason", timestamp = Clock.System.now(),
		)
		
		env = mockk(relaxUnitFun = true)
		every { env.agentState } returns agentState
		every { env.tools } returns tools
		every { env.settings } returns settings
		every { env.workspace } returns Workspace("test", false, tmpDir)
		every { env.containerConfig } returns ContainerConfig(workDir = tmpDir, workspaceHostPath = tmpDir)
		every { env.summarizeModel } returns model
		every { env.currentFallbackModels } returns null
		every { env.context } returns AgentContext(null, null, null, null, null)
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		capturedOutputs.clear()
		coEvery { env.emitOutput(any()) } answers { capturedOutputs.add(firstArg()) }
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
	}
	
	@AfterTest
	fun tearDown() {
		tmpDir.toFile().deleteRecursively()
	}
	
	@Test
	fun `successful execution returns Tool with SUCCESS status`() = runTest {
		val toolResult = AgentContext.Message.Tool(
			name = "bash_run", callId = "c1",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "test reason",
				timestamp = pendingCall.timestamp, model = model,
			),
			result = AgentContext.Message.Tool.Result(
				content = "execution output", timestamp = Clock.System.now(),
				status = AgentContext.Message.Tool.Result.Status.SUCCESS,
			),
		)
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } returns toolResult
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		assertEquals("execution output", result.result.content)
		assertTrue(statusLog.contains(AgentStatus.TOOL_CALLING))
	}
	
	@Test
	fun `cancellation returns Tool with CANCELLED status`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } throws
				CancellationException("cancelled")
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.CANCELLED, result.result.status)
		assertEquals("Tool cancelled", result.result.content)
	}
	
	@Test
	fun `generic exception returns Tool with FAILURE status`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } throws
				RuntimeException("Boom")
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.FAILURE, result.result.status)
		assertEquals("Boom", result.result.content)
	}
	
	@Test
	fun `generic exception with null message uses fallback`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } throws
				RuntimeException()
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.FAILURE, result.result.status)
		assertEquals("Tool execution failed", result.result.content)
	}
	
	@Test
	fun `timeout returns Tool with TIMEOUT status`() = runTest {
		val timeoutSettings = listOf(
			SettingItem(SettingKey("core.agent.tool.timeout.seconds"), SettingItem.Value.ValInt(0), ""),
			SettingItem(
				SettingKey("core.agent.tool.response.timeout"),
				SettingItem.Value.ValString("Timeout after %d seconds"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.response.property.missing"),
				SettingItem.Value.ValString("Missing %s"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.response.property.error"),
				SettingItem.Value.ValString("Error %s"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.response.function.name.error"),
				SettingItem.Value.ValString("Function %s not found"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.description.enable"),
				SettingItem.Value.ValString("Enable tool"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.response.active"),
				SettingItem.Value.ValString("Tool %s with %d functions enabled"),
				""
			),
			SettingItem(
				SettingKey("core.agent.tool.response.json.error"),
				SettingItem.Value.ValString("JSON error: %s"),
				""
			),
		)
		every { env.settings } returns timeoutSettings
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.TIMEOUT, result.result.status)
		assertEquals("Timeout after 0 seconds", result.result.content)
	}
	
	@Test
	fun `tool callbacks fire onToolActivated`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } coAnswers {
			val onToolActivated = arg<suspend (List<io.github.autotweaker.core.tool.Tool>) -> Unit>(4)
			onToolActivated.invoke(emptyList())
			toolResultForTest()
		}
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		val activation = capturedOutputs.firstOrNull { it is AgentOutput.ToolListUpdate }
		assertNotNull(activation)
	}
	
	@Test
	fun `tool callbacks fire onToolOutput`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any()) } coAnswers {
			val onToolOutput = arg<suspend (AgentOutput.ToolOutput) -> Unit>(5)
			onToolOutput.invoke(AgentOutput.ToolOutput("bash", "c1", "streaming data"))
			toolResultForTest()
		}
		
		val result = executeApprovedToolPhase(env, validationResult, pendingCall)
		
		assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, result.result.status)
		val toolOutput = capturedOutputs.firstOrNull { it is AgentOutput.ToolOutput }
		assertNotNull(toolOutput)
		assertEquals("streaming data", (toolOutput as AgentOutput.ToolOutput).content)
	}
	
	// region helpers
	
	private fun toolResultForTest() = AgentContext.Message.Tool(
		name = "bash_run", callId = "c1",
		call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "test reason",
			timestamp = pendingCall.timestamp, model = model,
		),
		result = AgentContext.Message.Tool.Result(
			content = "execution output", timestamp = Clock.System.now(),
			status = AgentContext.Message.Tool.Result.Status.SUCCESS,
		),
	)
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(name = "test-model", provider = provider, modelInfo = mockk(relaxed = true))
	}
	// endregion
}
