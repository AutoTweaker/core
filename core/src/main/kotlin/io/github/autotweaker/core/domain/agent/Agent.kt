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

import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.core.domain.agent.AgentModel.Companion.toModelConfig
import io.github.autotweaker.core.domain.agent.compact.CompactService
import io.github.autotweaker.core.domain.agent.runner.AgentContextManager
import io.github.autotweaker.core.domain.agent.runner.RoundRunner
import io.github.autotweaker.core.domain.agent.think.LlmService
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ToolCallingStage
import io.github.autotweaker.core.domain.agent.tool.ToolMap
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.agent.tool.TruncationImpl
import io.github.autotweaker.core.domain.session.AgentHost
import kotlinx.coroutines.flow.*
import java.nio.file.Path
import java.util.*

class Agent(
	context: RuntimeContext,
	val agentId: UUID,
	val name: KebabCase,
	model: AgentModel,
	private val workspace: () -> Path,
	private val tools: ToolMap,
	activeTools: Set<String>,
	@Suppress("unused") private val host: AgentHost,
) {
	private val _status = MutableStateFlow(AgentStatus.FREE)
	val status: StateFlow<AgentStatus> = _status.asStateFlow()
	
	private val _toolCalling = MutableStateFlow<Pair<String, ToolPresentation>?>(null)
	val toolCalling = _toolCalling.asStateFlow()
	
	private val _output = MutableSharedFlow<RuntimeOutput>(
		extraBufferCapacity = 64
	)
	val output: SharedFlow<RuntimeOutput> = _output.asSharedFlow()
	
	private val onOutput: (RuntimeOutput) -> Unit = {
		_output.tryEmit(it)
	}
	
	private val ctx = AgentContextManager(
		context.copy(currentRound = null),
	)
	val context: StateFlow<RuntimeContext> = ctx.context
	
	private val toolManager = Tools(workspace, tools, activeTools, agentId)
	val activeTools: StateFlow<Set<String>> = toolManager.activeTools
	
	private val truncation = TruncationImpl(workspace)
	private val llmService = LlmService(agentId, _status, onOutput)
	private val thinkingStage by lazy {
		ThinkingStage(llmService, toolManager, workspace, truncation, _status, onOutput)
	}
	private val toolCallingStage by lazy {
		ToolCallingStage(
			agentId = agentId,
			tools = toolManager,
			workspace = workspace,
			truncation = truncation,
			status = _status,
			onOutput = onOutput,
			onToolCall = { _toolCalling.value = it })
	}
	private val compact = CompactService(agentId, onOutput)
	
	private val runner = RoundRunner(
		ctx = ctx,
		workspace = workspace,
		tools = toolManager,
		thinkingStage = thinkingStage,
		toolCalling = toolCallingStage,
		compactService = compact,
		agentModel = model,
		status = _status,
		agentId = agentId,
	)
	
	val exception get() = runner.exception
	
	val model get() = runner.model.toModelConfig()
	
	suspend fun execute(command: AgentCommand) = also {
		runner.execute(command)
	}
	
	fun sendMessage(content: MessageContent): Delivery = runner.send(content)
	
	suspend fun updateInjections(
		function: (List<ContextInjection>?) -> List<ContextInjection>?
	) = ctx.updateInjections(function)
	
	suspend fun shutdown() {
		runner.shutdown()
	}
}
