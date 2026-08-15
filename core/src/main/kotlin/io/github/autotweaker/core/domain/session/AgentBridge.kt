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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.AgentAPI
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.UsageEntry
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.domain.agent.*
import io.github.autotweaker.core.domain.agent.tool.MetaCache
import io.github.autotweaker.core.domain.agent.tool.ToolMap
import io.github.autotweaker.core.domain.agent.tool.Tools.Companion.cacheMeta
import io.github.autotweaker.core.domain.agent.tool.Tools.Companion.name
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.domain.session.converter.AgentContextBuilder
import io.github.autotweaker.core.domain.session.converter.RuntimeContextBuilder
import io.github.autotweaker.core.domain.tool.CoreTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*

class AgentBridge(
	private val host: AgentHost,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val resolveModel: suspend (UUID) -> Model,
	workspace: Path,
) : AgentAPI, Loggable, Traceable {
	/* 初始化 */
	private val contextLock = ReentrantMutex()
	
	private lateinit var initialData: AgentData
	private lateinit var tools: ToolMap
	
	private val _context by lazy { MutableStateFlow(initialData.context) }
	override val context: StateFlow<AgentContext> by lazy { _context.asStateFlow() }
	private var droppedCompacted: AgentContextIndex.CompactedRounds? = null
	
	private var cwd = workspace
	
	private lateinit var _agent: Agent
	val agent get() = _agent
	
	private val _output = MutableSharedFlow<AgentOutput>(
		extraBufferCapacity = 64
	)
	override val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	override val id: UUID get() = _agent.agentId
	override val name: KebabCase get() = _agent.name
	override val status: StateFlow<AgentStatus> get() = _agent.status
	override val activeTools: StateFlow<Set<String>> get() = _agent.activeTools
	override val toolCalling: StateFlow<Pair<String, ToolPresentation>?> get() = _agent.toolCalling
	
	override val model: ModelConfig
		get() = _agent.model
	
	private val agentData
		get() = AgentData(
			id = id,
			name = name,
			model = _agent.model,
			context = _context.value,
			activeTools = activeTools.value
		)
	
	private val scope = scope()
	
	private val saveChannel = Channel<Unit>(Channel.CONFLATED)
	private var collectJob: Job? = null
	
	suspend fun init(data: AgentData) = also {
		initialData = data
		
		initTools(); createAgent()
		collectJob = scope.launch {
			_agent.context.collect { saveChannel.send(Unit) }
		}
		scope.launch {
			saveChannel.consumeEach {
				trace.catching { _agent.context.value.save() }
					.onFailure { e ->
						log.error("Failed to save agent context  agentId={}", _agent.agentId, e)
					}
			}
		}
		scope.launch {
			_agent.activeTools.collect {
				trace.catching { saveAgent() }
					.onFailure { e ->
						log.error("Failed to save agent data  agentId={}", _agent.agentId, e)
					}
			}
		}
		scope.launch {
			_agent.output.collect {
				trace.catching {
					it.toSessionOutput()?.let { result ->
						_output.tryEmit(result)
					}
				}.onFailure { e ->
					log.error("Failed to process agent output  agentId={}", _agent.agentId, e)
				}
			}
		}
		log.info("Initialized agent bridge  agentId={}  cwd={}", _agent.agentId, cwd)
	}
	
	private suspend fun initTools(): MetaCache {
		val coreTools = loadService<CoreTool<ToolArgs>>().associateBy { it.name() }
		val pluginTools = PluginLoader.load<Tool<ToolArgs>>().associateBy { it.name() }
		
		val all = coreTools + pluginTools
		tools = all
		return cacheMeta(all)
	}
	
	/* API */
	
	override fun send(content: MessageContent) =
		_agent.sendMessage(content)
			.andLog(log) {
				info("Sent user message  agentId={}  charCount={}", _agent.agentId, content.content?.length)
			}
	
	override suspend fun inject(injection: ContextInjection) = also {
		_agent.updateInjections { injections ->
			val oldInjections = injections.orEmpty()
			oldInjections.filterNot { it.id == injection.id } + injection
		}
	}
	
	override suspend fun removeInjection(id: UUID) = also {
		_agent.updateInjections { injections ->
			val oldInjections = injections ?: return@updateInjections injections
			oldInjections.filterNot { it.id == id }.orNull()
		}
	}
	
	override suspend fun pause() = also {
		_agent.execute(AgentCommand.Pause)
		saveAgent()
	}
	
	override suspend fun compact() = also {
		_agent.execute(AgentCommand.Compact)
	}
	
	override suspend fun cancelCompact() = also {
		_agent.execute(AgentCommand.CancelCompact)
	}
	
	override suspend fun cancelTool() = also {
		_agent.execute(AgentCommand.CancelTool)
	}
	
	override suspend fun approve(approval: ToolApprove) = also {
		_agent.execute(AgentCommand.ApproveTool(approval))
	}
	
	override suspend fun setModel(config: ModelConfig) = also {
		_agent.execute(
			AgentCommand.UpdateModel(
				model = config.toAgentModel()
			)
		)
		saveAgent()
		log.info("Updated agent model  agentId={}", _agent.agentId)
	}
	
	override suspend fun stop() = also {
		log.info("Initiated agent stop  agentId={}", _agent.agentId)
		_agent.execute(AgentCommand.Stop)
		saveAgent()
		log.info("Stopped agent  agentId={}", _agent.agentId)
	}
	
	suspend fun shutdown() {
		collectJob?.cancel()
		saveChannel.close()
		_agent.shutdown()
		scope.cancel()
		trace.catching { _agent.context.value.save() }
			.onFailure { e ->
				log.error("Failed to save agent context  agentId={}", _agent.agentId, e)
			}
		log.info("Completed agent bridge shutdown  agentId={}", _agent.agentId)
	}
	
	/* 内部工具 */
	
	private suspend fun RuntimeOutput.toSessionOutput(): AgentOutput? = when (this) {
		is RuntimeOutput.LlmDelta -> AgentOutput.LlmDelta(delta)
		is RuntimeOutput.LlmError -> AgentOutput.LlmError(
			error.error, error.statusCode, error.model, error.timestamp
		)
		
		is RuntimeOutput.Compact -> AgentOutput.Compact(output)
		is RuntimeOutput.Error -> AgentOutput.Error(error)
		is RuntimeOutput.Tool -> AgentOutput.Tool(output)
		is RuntimeOutput.UsageConsumed -> {
			val record = AgentMessage.UsageRecord(
				id = usage.id,
				timestamp = usage.timestamp,
				model = usage.modelId,
				usage = usage.usage,
			)
			sessionRepo.saveMessages(listOf(record))
			usageRepo.save(listOf(usage))
			
			updateContext {
				it.copy(droppedMessages = it.droppedMessages.orEmpty() + record.id)
			}.discard(null)
		}
	}
	
	
	private suspend fun createAgent() {
		_agent = Agent(
			agentId = initialData.id,
			context = RuntimeContextBuilder(_context.value, sessionRepo::loadMessages)().let {
				droppedCompacted = it.second
				return@let it.first
			},
			workspace = { cwd },
			model = initialData.model.toAgentModel(),
			tools = tools,
			activeTools = initialData.activeTools,
			host = host,
			name = initialData.name
		)
	}
	
	private suspend fun RuntimeContext.save() = contextLock.withLock {
		val builder = AgentContextBuilder(_context.value, this, droppedCompacted)
		val (context, messages) = builder()
		
		messages.save()
		updateContext { context }
	}
	
	private suspend fun updateContext(function: (AgentContext) -> AgentContext) {
		_context.update(function)
		saveAgent()
	}
	
	private suspend fun List<AgentMessage>.save() {
		sessionRepo.saveMessages(this)
		usageRepo.save(mapNotNull { message ->
			when (message) {
				is AgentMessage.Assistant -> message.usage?.let {
					UsageEntry(message.id, message.model, message.timestamp, it)
				}
				
				is AgentMessage.Compact -> message.usage?.let {
					UsageEntry(message.id, message.model, message.timestamp, it)
				}
				
				is AgentMessage.UsageRecord ->
					UsageEntry(message.id, message.model, message.timestamp, message.usage)
				
				else -> null
			}
		})
	}
	
	private suspend fun saveAgent() = contextLock.withLock {
		sessionRepo.saveAgent(agentData)
	}
	
	private suspend fun ModelConfig.toAgentModel() = AgentModel(
		model = resolveModel(model),
		summarize = resolveModel(summarize),
		compact = resolveModel(compact),
		fallback = fallback.map { resolveModel(it) },
		thinking = thinking
	)
}
