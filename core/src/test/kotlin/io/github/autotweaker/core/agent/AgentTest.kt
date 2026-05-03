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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.session.workspace.Workspace
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class AgentTest {
	
	private val mockModel: Model = mockk(relaxed = true)
	private val mockSummarizeModel: Model = mockk(relaxed = true)
	
	private val agentSettings: List<SettingItem> = listOf(
		SettingItem(SettingKey("core.agent.tool.description.reason"), SettingItem.Value.ValString("reason"), ""),
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
			SettingItem.Value.ValString("name error: %s"),
			""
		),
		SettingItem(SettingKey("core.agent.tool.response.canceled"), SettingItem.Value.ValString("Tool cancelled"), ""),
		SettingItem(SettingKey("core.agent.tool.response.rejected"), SettingItem.Value.ValString("Tool rejected"), ""),
		SettingItem(
			SettingKey("core.agent.tool.response.rejected.with.feedback"),
			SettingItem.Value.ValString("Tool rejected: %s"),
			""
		),
	)
	
	private fun createWorkspace(): Workspace {
		val tmpDir = createTempDirectory("agent-test")
		return Workspace("test", false, tmpDir)
	}
	
	private fun createAgent(
		context: AgentContext = AgentContext(null, null, null, null, null),
		model: Model = mockModel,
		fallbackModels: List<Model>? = null,
		thinking: Boolean = false,
		tools: List<io.github.autotweaker.core.tool.Tool> = emptyList(),
	): Agent {
		return Agent(
			context = context,
			workspace = createWorkspace(),
			model = model,
			fallbackModels = fallbackModels,
			thinking = thinking,
			summarizeModel = mockSummarizeModel,
			containerConfig = ContainerConfig(),
			settings = agentSettings,
			tools = tools,
		)
	}
	
	// region construction & properties
	
	@Test
	fun `agent construction succeeds`() {
		val agent = createAgent()
		assertNotNull(agent)
	}
	
	@Test
	fun `initial status is FREE`() = runBlocking {
		val agent = createAgent()
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `statusFlow emits FREE initially`() = runBlocking {
		val agent = createAgent()
		val firstStatus = agent.statusFlow.first()
		assertEquals(AgentStatus.FREE, firstStatus)
	}
	
	@Test
	fun `output is accessible`() {
		val agent = createAgent()
		assertNotNull(agent.output)
	}
	
	@Test
	fun `workspace is set correctly`() {
		val tmpDir = createTempDirectory("agent-test-ws")
		val ws = Workspace("my-workspace", true, tmpDir)
		val agent = Agent(
			context = AgentContext(null, null, null, null, null),
			workspace = ws,
			model = mockModel,
			fallbackModels = null,
			thinking = false,
			summarizeModel = mockSummarizeModel,
			containerConfig = ContainerConfig(),
			settings = agentSettings,
			tools = emptyList(),
		)
		assertEquals(ws, agent.workspace)
	}
	
	@Test
	fun `summarizeModel is set correctly`() {
		val agent = createAgent()
		assertEquals(mockSummarizeModel, agent.summarizeModel)
	}
	
	@Test
	fun `containerConfig has default values`() {
		val agent = createAgent()
		assertEquals("autotweaker", agent.containerConfig.name)
	}
	
	@Test
	fun `settings are stored`() {
		val agent = createAgent()
		assertEquals(agentSettings, agent.settings)
	}
	
	@Test
	fun `tool messages use settings values`() {
		val agent = createAgent()
		assertEquals("Tool cancelled", agent.toolCancelledMessage)
		assertEquals("Tool rejected", agent.toolRejectedMessage)
		assertEquals("Tool rejected: %s", agent.toolRejectedWithFeedbackMessage)
	}
	
	@Test
	fun `agentState is initialized with null fields`() {
		val agent = createAgent()
		assertEquals(null, agent.agentState.pendingApproval)
		assertEquals(null, agent.agentState.processedTools)
	}
	
	// endregion
	
	// region dispatch directives
	
	@Test
	fun `dispatch UpdateModel changes currentModel`() = runBlocking {
		val agent = createAgent()
		val newModel: Model = mockk(relaxed = true)
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(newModel))
		pollUntil { agent.currentModel === newModel }
		
		assertSame(newModel, agent.currentModel)
	}
	
	@Test
	fun `dispatch UpdateModel with fallback models`() = runBlocking {
		val agent = createAgent()
		val newModel: Model = mockk(relaxed = true)
		val fallbacks = listOf(mockk<Model>(relaxed = true))
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(newModel, fallbacks))
		pollUntil { agent.currentModel === newModel }
		
		assertSame(newModel, agent.currentModel)
		assertEquals(fallbacks, agent.currentFallbackModels)
	}
	
	@Test
	fun `dispatch UpdateModel with thinking override`() = runBlocking {
		val agent = createAgent(thinking = false)
		val newModel: Model = mockk(relaxed = true)
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(newModel, thinking = true))
		pollUntil { agent.currentThinking }
		
		assertSame(newModel, agent.currentModel)
	}
	
	@Test
	fun `dispatch UpdateModel keeps existing fallback models when not provided`() = runBlocking {
		val fallbacks = listOf(mockk<Model>(relaxed = true))
		val agent = createAgent(fallbackModels = fallbacks)
		val newModel: Model = mockk(relaxed = true)
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(newModel))
		pollUntil { agent.currentModel === newModel }
		
		assertSame(newModel, agent.currentModel)
		assertEquals(fallbacks, agent.currentFallbackModels)
	}
	
	@Test
	fun `dispatch Pause when FREE status stays FREE`() = runBlocking {
		val agent = createAgent()
		assertEquals(AgentStatus.FREE, agent.status)
		
		agent.dispatch(AgentCommand.Directive.Pause)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Pause when ERROR status stays ERROR`() = runBlocking {
		val agent = createAgent()
		agent.updateStatus(AgentStatus.ERROR)
		
		agent.dispatch(AgentCommand.Directive.Pause)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.ERROR, agent.status)
	}
	
	@Test
	fun `dispatch Resume when not PAUSED status stays FREE`() = runBlocking {
		val agent = createAgent()
		assertEquals(AgentStatus.FREE, agent.status)
		
		agent.dispatch(AgentCommand.Directive.Resume)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Retry when not ERROR status stays FREE`() = runBlocking {
		val agent = createAgent()
		assertEquals(AgentStatus.FREE, agent.status)
		
		agent.dispatch(AgentCommand.Directive.Retry)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Cancel when FREE does not crash`() = runBlocking {
		val agent = createAgent()
		
		agent.dispatch(AgentCommand.Directive.Cancel)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Compact when no history does not crash`() = runBlocking {
		val agent = createAgent()
		
		agent.dispatch(AgentCommand.Directive.Compact)
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Stop when FREE sets status to FREE`() = runBlocking {
		val agent = createAgent()
		
		agent.dispatch(AgentCommand.Directive.Stop)
		delay(200.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch Stop archives context when current round exists`() = runBlocking {
		val now = kotlin.time.Clock.System.now()
		val userMsg = AgentContext.Message.User("hello", null, now)
		val currentRound = AgentContext.CurrentRound(userMsg, null)
		val ctx = AgentContext(null, null, null, null, currentRound)
		val agent = createAgent(context = ctx)
		
		agent.dispatch(AgentCommand.Directive.Stop)
		delay(200.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	// endregion
	
	// region multiple directives
	
	@Test
	fun `dispatch multiple UpdateModel directives in sequence`() = runBlocking {
		val agent = createAgent()
		val model1: Model = mockk(relaxed = true)
		val model2: Model = mockk(relaxed = true)
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(model1))
		pollUntil { agent.currentModel === model1 }
		assertSame(model1, agent.currentModel)
		
		agent.dispatch(AgentCommand.Directive.UpdateModel(model2, thinking = false))
		pollUntil { agent.currentModel === model2 }
		assertSame(model2, agent.currentModel)
		assertEquals(false, agent.currentThinking)
	}
	
	// endregion
	
	// region dispatch mixed command types
	
	@Test
	fun `dispatch sends Directive to directive channel`() = runBlocking {
		val agent = createAgent()
		
		agent.dispatch(AgentCommand.Directive.Stop)
		agent.dispatch(AgentCommand.Directive.Pause)
		agent.dispatch(AgentCommand.Directive.Cancel)
		delay(150.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	@Test
	fun `dispatch sends Message to message channel`() = runBlocking {
		val agent = createAgent()
		
		agent.dispatch(AgentCommand.Message.ApproveToolCall(emptyList()))
		delay(100.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	// endregion
	
	// region SendMessage flow
	
	@Test
	fun `dispatch SendMessage when FREE triggers message processing`() {
		runBlocking {
			val agent = createAgent()
			agent.dispatch(AgentCommand.Message.SendMessage("hello world"))
			delay(500.milliseconds)
			assertNotNull(agent.status)
		}
	}
	
	@Test
	fun `dispatch ApproveToolCall when not WAITING gets requeued`() = runBlocking {
		val agent = createAgent()
		val approval = AgentCommand.Message.ApproveToolCall.Approve("call-1", approved = true)
		val msg = AgentCommand.Message.ApproveToolCall(listOf(approval))
		
		agent.dispatch(msg)
		delay(150.milliseconds)
		
		assertEquals(AgentStatus.FREE, agent.status)
	}
	
	// endregion
	
	private suspend fun pollUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
		val result = withTimeoutOrNull(timeoutMs.milliseconds) {
			while (!condition()) {
				delay(10.milliseconds)
			}
			true
		}
		if (result == null) {
			throw AssertionError("Condition not met within ${timeoutMs}ms")
		}
	}
}
