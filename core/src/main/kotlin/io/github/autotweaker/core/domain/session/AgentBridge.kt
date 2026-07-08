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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.domain.agent.*
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.session.converter.AgentContextBuilder
import io.github.autotweaker.core.domain.session.converter.RuntimeContextBuilder
import io.github.autotweaker.core.domain.tool.CoreTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AgentBridge(
	private val host: AgentHost,
	private val store: SessionRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private val workspace: WorkspaceMeta,
) : AgentAPI, Loggable, Settable {
	/* 初始化 */
	private val contextLock = ReentrantMutex()
	private val injectLock = ReentrantMutex()
	
	private lateinit var initialData: AgentData
	private lateinit var tools: List<Tool<ToolArgs>>
	
	private val _context = MutableStateFlow(initialData.context)
	override val context: StateFlow<AgentContext> = _context.asStateFlow()
	
	private val messages = ConcurrentHashMap<UUID, AgentMessage>()
	
	private lateinit var _agent: Agent
	val agent get() = _agent
	
	private val _output = MutableSharedFlow<AgentOutput>(
		extraBufferCapacity = 64
	)
	override val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	override val id: UUID get() = _agent.agentId
	override val name: KebabCase get() = _agent.name
	override val status: StateFlow<AgentStatus> get() = _agent.status
	override val toolInfo: StateFlow<List<ToolInfo>> get() = _agent.toolInfo
	override val toolCalling: StateFlow<String?> get() = _agent.toolCalling
	
	override val model: ModelConfig
		get() = _agent.model
	
	private val agentData
		get() = AgentData(
			id = id,
			name = name,
			model = _agent.model,
			context = _context.value,
			activeTools = toolInfo.value.filter { it.active }.map { it.name }
		)
	
	private val scope = scope()
	
	private val saveChannel = Channel<Unit>(Channel.CONFLATED)
	private var collectJob: Job? = null
	
	suspend fun init(data: AgentData) = also {
		initialData = data
		
		val ids = _context.value.index.ids().orNull()
		val loaded = ids?.let { store.loadMessages(it.toList()) }?.orNull()
		loaded?.forEach { messages[it.id] = it }
		loaded?.let { UsageStore.collect(it) }
		
		initTools()
		createAgent()
		collectJob = scope.launch {
			_agent.context.collect { saveChannel.send(Unit) }
		}
		scope.launch {
			saveChannel.consumeEach { _agent.context.value.save() }
		}
		scope.launch {
			_agent.output.collect {
				it.toSessionOutput()?.let { result ->
					_output.tryEmit(result)
				}
			}
		}
		log.info("Initialized agent bridge  agentId={}  workspace={}", _agent.agentId, workspace.displayName)
	}
	
	private fun initTools() {
		val coreTools = ServiceLoader.load(CoreTool::class.java).toList()
		val duplicates = coreTools.map { it.name }.groupingBy { it }.eachCount().filter { it.value > 1 }
		check(duplicates.isEmpty()) { "Duplicate CoreTool: ${duplicates.keys}" }
		
		val pluginTools = PluginLoader.load<Tool<ToolArgs>>().distinctBy { it.name }
		
		val pluginNames = pluginTools.map { it.name }.toSet()
		@Suppress("UNCHECKED_CAST")
		tools = pluginTools + (coreTools as List<CoreTool<ToolArgs>>).filter { it.name !in pluginNames }
	}
	
	/* API */
	
	override fun send(content: MessageContent) =
		_agent.sendMessage(content)
			.andLog(log) {
				info("Sent user message  agentId={}  charCount={}", _agent.agentId, content.content?.length)
			}
	
	override suspend fun inject(injection: ContextInjection) = also {
		injectLock.withLock {
			val oldInjections = agentData.context.injections.orEmpty()
			val new = (oldInjections.filterNot { it.id == injection.id } + injection)
			_agent.updateInjections(new)
		}
	}
	
	override suspend fun removeInjection(id: UUID) = also {
		injectLock.withLock {
			val oldInjections = agentData.context.injections ?: return@withLock
			val new = oldInjections.filterNot { it.id == id }.orNull()
			_agent.updateInjections(new)
		}
	}
	
	override suspend fun pause() = also {
		_agent.execute(AgentCommand.Pause)
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
		).andSave()
		log.info("Updated agent model  agentId={}", _agent.agentId)
	}
	
	override suspend fun stop() = also {
		log.info("Initiated agent stop  agentId={}", _agent.agentId)
		_agent.execute(AgentCommand.Stop).andSave()
		log.info("Stopped agent  agentId={}", _agent.agentId)
	}
	
	suspend fun shutdown() = also {
		collectJob?.cancel()
		saveChannel.close()
		_agent.shutdown()
		scope.cancel()
		_agent.context.value.save()
		log.info("Completed agent bridge shutdown  agentId={}", _agent.agentId)
	}
	
	/* 内部工具 */
	
	private suspend fun RuntimeOutput.toSessionOutput(): AgentOutput? = when (this) {
		is RuntimeOutput.LlmDelta -> AgentOutput.LlmDelta(delta)
		is RuntimeOutput.LlmError -> AgentOutput.LlmError(
			error.content, error.statusCode, error.model, error.timestamp
		)
		
		is RuntimeOutput.Compact -> AgentOutput.Compact(output)
		is RuntimeOutput.Error -> AgentOutput.Error(error)
		is RuntimeOutput.Tool -> AgentOutput.Tool(output)
		is RuntimeOutput.UsageConsumed -> {
			val record = AgentMessage.UsageRecord(
				id = UUID.randomUUID(),
				timestamp = timestamp,
				snapshot = UsageSnapshot(usage, modelInfo),
			)
			let { messages[record.id] = record }.andSave()
			contextLock.withLock {
				val ctx = _context.value
				updateContext(
					ctx.copy(droppedMessages = ctx.droppedMessages.orEmpty() + record.id)
				)
			}.discard(null)
		}
	}
	
	
	private suspend fun createAgent() {
		_agent = Agent(
			agentId = id,
			context = RuntimeContextBuilder(_context.value, messages)(),
			workspace = workspace,
			tools = tools,
			activeTools = initialData.activeTools,
			host = host,
			name = initialData.name
		).init(
			model = initialData.model.toAgentModel()
		)
	}
	
	private suspend fun RuntimeContext.save() = contextLock.withLock {
		val builder = AgentContextBuilder(_context.value, this)
		val (context, messages) = builder()
		
		updateContext(context)
		messages.save()
	}
	
	private suspend fun updateContext(context: AgentContext) =
		_context.update { context }.andSave()
	
	
	private suspend fun List<AgentMessage>.save() {
		forEach { messages[it.id] = it }.andSave()
		UsageStore.collect(this)
	}
	
	private suspend fun <T> T.andSave(): T = also {
		store.saveAgent(agentData)
		if (messages.isNotEmpty()) {
			store.saveMessages(messages.values.toList())
		}
	}
	
	private suspend fun ModelConfig.toAgentModel() = AgentModel(
		model = resolveModel(model),
		summarize = resolveModel(summarize),
		compact = resolveModel(compact),
		fallback = fallback.map { resolveModel(it) },
		thinking = thinking
	)
}
