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

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentModel.Companion.toModelConfig
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.runner.RoundRunner
import io.github.autotweaker.core.domain.agent.think.LlmService
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.ToolMap
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.session.AgentHost
import kotlinx.coroutines.flow.*
import java.util.*

class Agent(
	context: RuntimeContext,
	val agentId: UUID,
	val name: KebabCase,
	model: AgentModel,
	private val workspace: WorkspaceMeta,
	private val tools: ToolMap,
	activeTools: Set<String>,
	@Suppress("unused") private val host: AgentHost,
) : Settable {
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val status: StateFlow<AgentStatus> = _status.asStateFlow()
	
	private val _toolCalling = MutableStateFlow<String?>(null)
	val toolCalling = _toolCalling.asStateFlow()
	
	private val _output = MutableSharedFlow<RuntimeOutput>()
	val output: SharedFlow<RuntimeOutput> = _output.asSharedFlow()
	
	private val ctx = AgentContextManager(
		context.copy(currentRound = null),
		setting(AgentToolSettings.Cancelled())
	)
	val context: StateFlow<RuntimeContext> = ctx.context
	
	private val toolManager = Tools(tools, activeTools, agentId)
	val activeTools: StateFlow<Set<String>> = toolManager.activeTools
	
	private val llmService = LlmService(agentId) { _output.tryEmit(it) }
	private val thinkingStage by lazy { ThinkingStage(llmService, toolManager) }
	private val toolCallingStage by lazy {
		ToolCallingStage(
			agentId = agentId,
			tools = toolManager,
			workspace = workspace,
			onOutput = { _output.tryEmit(it) },
			onToolCall = { _toolCalling.update { it } })
	}
	private val compact = CompactService(agentId) { _output.tryEmit(it) }
	
	private val runner = RoundRunner(
		ctx = ctx,
		tools = toolManager,
		thinkingStage = thinkingStage,
		toolCalling = toolCallingStage,
		compactService = compact,
		agentModel = model,
		statusFlow = _status,
		agentId = agentId,
	)
	
	val model get() = runner.model.toModelConfig()
	
	suspend fun execute(command: AgentCommand) = also {
		runner.execute(command)
	}
	
	fun sendMessage(content: MessageContent): Delivery = runner.send(content)
	
	suspend fun updateInjections(injections: List<ContextInjection>?) = also {
		ctx.updateInjections(injections)
	}
	
	suspend fun shutdown() {
		runner.shutdown()
	}
}
