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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.adapter.AgentAPI
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.log
import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.KebabId
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.Delivery
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolInfo
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.domain.agent.*
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.session.converter.AgentContextConverter
import io.github.autotweaker.core.domain.session.converter.SessionContextConverter
import io.github.autotweaker.core.domain.tool.CoreTool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AgentBridge(
	private val host: AgentHost,
	private val store: SessionRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private val workspace: WorkspaceMeta,
	private val secretStore: SecretStore,
	private val maxCompactedRounds: Int = 0,
) : AgentAPI, Loggable, Settable {
	/* 初始化 */
	private val saveMutex = Mutex()
	private val injectMutex = Mutex()
	
	private lateinit var initialData: AgentData
	private lateinit var tools: List<Tool<ToolArgs>>
	
	private val _context = MutableStateFlow(initialData.context)
	override val context: StateFlow<SessionContext> = _context.asStateFlow()
	
	private val messages = ConcurrentHashMap<UUID, SessionMessage>()
	
	private lateinit var _agent: Agent
	val agent get() = _agent
	
	private val _output = MutableSharedFlow<SessionOutput>(
		extraBufferCapacity = 64
	)
	override val output: SharedFlow<SessionOutput> = _output.asSharedFlow()
	
	override val id: UUID get() = _agent.agentId
	override val name: KebabId get() = _agent.name
	override val status: StateFlow<AgentStatus> get() = _agent.status
	override val toolInfo: StateFlow<List<ToolInfo>> get() = _agent.toolInfo
	
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
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	suspend fun init(data: AgentData) = also {
		initialData = data
		
		val ids = collectMessageIds(_context.value, maxCompactedRounds).toList().orNull()
		val loaded = ids?.let { store.loadMessages(it) }?.orNull()
		loaded?.forEach { messages[it.id] = it }
		loaded?.let { UsageStore.collect(it) }
		
		initTools()
		createAgent()
		scope.launch {
			_agent.context.collectLatest { it.save() }
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
	
	private suspend fun initTools() {
		val coreTools = ServiceLoader.load(CoreTool::class.java).toList()
		coreTools.forEach { it.init(secretStore) }
		val duplicates = coreTools.map { it.name }.groupingBy { it }.eachCount().filter { it.value > 1 }
		check(duplicates.isEmpty()) { "Duplicate CoreTool: ${duplicates.keys}" }
		
		val pluginTools = PluginLoader.load<Tool<ToolArgs>>().distinctBy { it.name }
		
		val pluginNames = pluginTools.map { it.name }.toSet()
		@Suppress("UNCHECKED_CAST")
		tools = pluginTools + (coreTools as List<CoreTool<ToolArgs>>).filter { it.name !in pluginNames }
	}
	
	/* API */
	
	override fun send(content: MessageContent) =
		_agent.sendMessage(content).andLog(log) {
			info("Sent user message  agentId={}  charCount={}", _agent.agentId, content.content?.length)
		}
	
	override suspend fun inject(injection: ContextInjection) = also {
		injectMutex.withLock {
			val oldInjections = agentData.context.injections.orEmpty()
			val new = (oldInjections.filterNot { it.id == injection.id } + injection).orNull()
			_agent.updateInjections(new)
		}
	}
	
	override suspend fun removeInjection(id: UUID) = also {
		injectMutex.withLock {
			val oldInjections = agentData.context.injections.orEmpty()
			val new = oldInjections.filterNot { it.id == id }.orNull()
			_agent.updateInjections(new)
		}
	}
	
	override suspend fun pause() = also { _agent.execute(AgentCommand.Pause) }
	override suspend fun compact() = also { _agent.execute(AgentCommand.Compact) }
	override suspend fun cancelCompact() = also { _agent.execute(AgentCommand.CancelCompact) }
	override suspend fun cancelTool() = also { _agent.execute(AgentCommand.CancelTool) }
	override suspend fun approve(approval: ToolApprove) = also { _agent.execute(AgentCommand.ApproveTool(approval)) }
	
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
		_agent.shutdown()
		scope.cancel()
		log.info("Completed agent bridge shutdown  agentId={}", _agent.agentId)
	}
	
	/* 内部工具 */
	
	private suspend fun AgentOutput.toSessionOutput(): SessionOutput? = when (this) {
		is AgentOutput.LlmDelta -> SessionOutput.LlmDelta(delta)
		is AgentOutput.LlmError -> SessionOutput.LlmError(
			error.content, error.statusCode, error.model, error.timestamp
		)
		
		is AgentOutput.Compact -> SessionOutput.Compact(output)
		is AgentOutput.Error -> SessionOutput.Error(error)
		is AgentOutput.Tool -> SessionOutput.Tool(output)
		is AgentOutput.UsageConsumed -> {
			val record = SessionMessage.UsageRecord(
				id = UUID.randomUUID(),
				timestamp = timestamp,
				snapshot = UsageSnapshot(usage, modelInfo),
			)
			messages[record.id] = record
			null.andSave()
		}
	}
	
	
	private suspend fun createAgent() {
		_agent = Agent(
			context = buildAgentContext(),
			workspace = workspace,
			tools = tools,
			activeTools = initialData.activeTools,
			host = host,
			name = initialData.name
		).init(
			model = initialData.model.toAgentModel()
		)
	}
	
	private fun buildAgentContext(): AgentContext {
		return SessionContextConverter.buildAgentContext(
			context = _context.value, messages = messages.values.toList(), maxCompactedRounds = maxCompactedRounds
		)
	}
	
	private suspend fun AgentContext.save() = saveMutex.withLock {
		val oldCtx = _context.value
		val result = AgentContextConverter.sync(this, oldCtx)
		result.messages.save()
		updateContext(
			SessionContext(
				systemPrompt = oldCtx.systemPrompt,
				index = result.index,
				droppedMessages = result.droppedMessageIds,
				injections = oldCtx.injections
			)
		)
	}
	
	private suspend fun updateContext(context: SessionContext) =
		_context.update { context }.andSave()
	
	
	private suspend fun List<SessionMessage>.save() {
		forEach { messages[it.id] = it }.andSave()
		UsageStore.collect(this)
	}
	
	private suspend fun <T> T.andSave(): T = also {
		store.saveAgent(agentData)
		if (messages.isNotEmpty()) {
			store.saveMessages(messages.values.toList())
		}
	}
	
	private fun collectMessageIds(ctx: SessionContext, maxCompactedRounds: Int): Set<UUID> {
		val ids = mutableSetOf<UUID>()
		val index = ctx.index
		
		index.compactedRounds?.takeLast(maxCompactedRounds)?.forEach { compacted ->
			ids.add(compacted.summarizedMessage)
			compacted.rounds.forEach { round ->
				SessionContextIndex.collectCompletedRoundIds(round, ids)
			}
		}
		index.historyRounds?.forEach { SessionContextIndex.collectCompletedRoundIds(it, ids) }
		index.currentRound?.let { SessionContextIndex.collectCurrentRoundIds(it, ids) }
		index.summarizedMessage?.let { ids.add(it) }
		
		return ids
	}
	
	private suspend fun ModelConfig.toAgentModel() = AgentModel(
		model = resolveModel(model),
		summarize = resolveModel(summarize),
		compact = resolveModel(compact),
		fallback = fallback.map { resolveModel(it) },
		thinking = thinking
	)
}
