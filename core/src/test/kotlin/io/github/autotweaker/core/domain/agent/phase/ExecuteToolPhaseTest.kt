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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ToolOutput
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.MutableAgentState
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.tool.Tool
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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
	private val settings = mockk<SettingService>().also { svc ->
		every { svc.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	
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
			callId = "c1", assistantMessageId = UUID.randomUUID(), name = "bash_run", modelId = model.id,
			arguments = "{}", reason = "test reason", timestamp = Clock.System.now(),
		)
		
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.tools } returns tools
		every { env.service } returns settings
		every { env.workspace } returns WorkspaceMeta("test", false, tmpDir)
		every { env.containerConfig } returns ContainerConfig(workDir = tmpDir, workspaceHostPath = tmpDir)
		every { env.summarizeModel } returns model
		every { env.currentFallbackModels } returns null
		every { env.context } returns MutableStateFlow(AgentContext(null, null, null, null, null))
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		capturedOutputs.clear()
		coEvery { env.emitOutput(any()) } answers { capturedOutputs.add(firstArg()) }
		every { env.updateStatus(any()) } answers { statusLog.add(firstArg()) }
		ToolProvider.init(mockk<ShellExecutor>(relaxed = true), mockk<RawFileSystem>(relaxed = true))
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
				timestamp = pendingCall.timestamp, modelId = model.id,
			),
			result = AgentContext.Message.Tool.Result(
				content = "execution output", timestamp = Clock.System.now(),
				status = ToolResultStatus.SUCCESS,
			),
		)
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } returns toolResult
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		assertEquals("execution output", result.result.content)
		assertTrue(statusLog.contains(AgentStatus.TOOL_CALLING))
	}
	
	@Test
	fun `cancellation returns Tool with CANCELLED status`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } throws
				CancellationException("cancelled")
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.CANCELLED, result.result.status)
		assertEquals("Tool cancelled", result.result.content)
	}
	
	@Test
	fun `generic exception returns Tool with FAILURE status`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } throws
				RuntimeException("Boom")
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
		assertEquals("Boom", result.result.content)
	}
	
	@Test
	fun `generic exception with null message uses fallback`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } throws
				RuntimeException()
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.FAILURE, result.result.status)
		assertEquals("Tool execution failed", result.result.content)
	}
	
	@Test
	fun `tool callbacks fire onToolActivated`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
			val onToolActivated = arg<suspend (List<Tool>) -> Unit>(5)
			onToolActivated.invoke(emptyList())
			toolResultForTest()
		}
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		val activation = capturedOutputs.firstOrNull { it is AgentOutput.ToolListUpdate }
		assertNotNull(activation)
	}
	
	@Test
	fun `tool callbacks fire onToolOutput`() = runTest {
		coEvery { tools.executeTool(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
			val onToolOutput = arg<suspend (AgentOutput.Tool) -> Unit>(6)
			onToolOutput.invoke(AgentOutput.Tool(ToolOutput(name = "bash", callId = "c1", content = "streaming data")))
			toolResultForTest()
		}
		
		val result = ExecuteToolPhase.execute(env, validationResult, pendingCall)
		
		assertEquals(ToolResultStatus.SUCCESS, result.result.status)
		val toolOutput = capturedOutputs.firstOrNull { it is AgentOutput.Tool }
		assertNotNull(toolOutput)
		assertEquals("streaming data", (toolOutput as AgentOutput.Tool).output.content)
	}
	
	// region helpers
	
	private fun toolResultForTest() = AgentContext.Message.Tool(
		name = "bash_run", callId = "c1",
		call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "test reason",
			timestamp = pendingCall.timestamp, modelId = model.id,
		),
		result = AgentContext.Message.Tool.Result(
			content = "execution output", timestamp = Clock.System.now(),
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), id = UUID.randomUUID()
		)
	}
	// endregion
}
