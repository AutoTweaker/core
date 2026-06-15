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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.agent.ToolInfo
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.runner.RoundRunner
import io.github.autotweaker.core.domain.agent.think.LlmService
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.agent.tool.Tools.Companion.buildToolInfo
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.flow.*
import java.util.*

class Agent(
	context: AgentContext,
	val agentId: UUID = UUID.randomUUID(),
	private val workspace: WorkspaceMeta,
	private val model: Model,
	private val summarizeModel: Model,
	private val fallbackModels: List<Model>?,
	private val thinking: Boolean,
	private val containerConfig: ContainerConfig,
	private val service: SettingService,
	private val tools: List<Tool<ToolArgs>>,
	private val activeTools: List<String>
) {
	init {
		check(context.currentRound == null)
		check(activeTools.all { it in tools.map { tool -> tool.name } })
	}
	
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val status: StateFlow<AgentStatus> = _status.asStateFlow()
	
	private val _output = MutableSharedFlow<AgentOutput>()
	val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	private val ctx = AgentContextManager(context, service.get(AgentToolSettings.Cancelled()).value)
	private lateinit var toolManager: Tools
	val toolInfo: StateFlow<List<ToolInfo>> get() = toolManager.toolInfo
	
	private val llmService = LlmService(agentId) { _output.emit(it) }
	private val thinkingStage by lazy { ThinkingStage(llmService, toolManager) }
	private val toolCalling by lazy { ToolCallingStage(agentId, toolManager, service) { _output.emit(it) } }
	private val compact = CompactService(agentId) { _output.emit(it) }
	
	private lateinit var runner: RoundRunner
	
	suspend fun init() {
		val info = tools.map { buildToolInfo(it, it.name in activeTools) }
		toolManager = Tools(info, service, tools, agentId)
		runner = RoundRunner(
			workspace = workspace,
			containerConfig = containerConfig,
			ctx = ctx,
			tools = toolManager,
			thinkingStage = thinkingStage,
			toolCalling = toolCalling,
			compactService = compact,
			model = model,
			summarizeModel = summarizeModel,
			fallbackModels = fallbackModels,
			thinking = thinking,
			service = service,
			statusFlow = _status,
			agentId = agentId,
		)
		runner.start()
	}
	
	suspend fun execute(command: AgentCommand) = runner.execute(command)
	
	fun sendMessage(content: MessageContent) = runner.send(content)
	
	suspend fun updateInjections(injections: List<ContextInjection>?) = ctx.updateInjections(injections)
	
	fun shutdown() {
		check(_status.value == AgentStatus.FREE)
		runner.shutdown()
	}
}
