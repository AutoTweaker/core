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

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.api.types.session.SessionContextIndex.CurrentRound
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.domain.agent.Agent
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.session.converter.AgentContextConverter
import io.github.autotweaker.core.domain.session.converter.SessionContextConverter
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class Session(
	data: SessionData,
	context: SessionContext,
	private val store: SessionRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private var workspace: WorkspaceMeta,
	private val containerConfig: ContainerConfig,
	private val service: SettingService,
	private val secretStore: SecretStore,
	private val maxCompactedRounds: Int = 0,
) {
	//region 初始化
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private val _coreTools = ServiceLoader.load(CoreTool::class.java).toList().also {
		val duplicates = it.map { t -> t.meta.name }.groupingBy { n -> n }.eachCount().filter { e -> e.value > 1 }
		require(duplicates.isEmpty()) { "Duplicate CoreTool: ${duplicates.keys}" }
	}
	private val _pluginTools = PluginLoader.load<Tool>().distinctBy { it.meta.name }
	private val _tools: List<Tool> = run {
		val coreNames = _coreTools.map { it.meta.name }.toSet()
		_pluginTools + _coreTools.filter { it.meta.name !in coreNames }
	}
	
	private val _data = MutableStateFlow(data)
	val data: StateFlow<SessionData> = _data.asStateFlow()
	
	private val _context = MutableStateFlow(context)
	val context: StateFlow<SessionContext> = _context.asStateFlow()
	
	private val messages = ConcurrentHashMap<UUID, SessionMessage>()
	
	private val agents = mutableMapOf<UUID, Agent>()
	private val agent: Agent? get() = agents.values.firstOrNull()
	
	private val _sessionOutput = MutableSharedFlow<SessionOutput>(
		extraBufferCapacity = 64
	)
	val output = _sessionOutput.asSharedFlow()
	
	val agentStatus: StateFlow<AgentStatus> get() = agent?.statusFlow ?: error("No agent created")
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	suspend fun init() {
		val ids = collectMessageIds(_context.value, maxCompactedRounds).toList()
		if (ids.isNotEmpty()) {
			val loaded = store.loadMessages(ids)
			loaded?.forEach { messages[it.id] = it }
			loaded?.let { UsageStore.collect(it) }
		}
		createAgent()
		agent?.init()
		scope.launch {
			agent?.context?.collectLatest { syncContext(it) }
		}
		scope.launch {
			agent?.output?.collect {
				val output = processAgentOutput(it) ?: return@collect
				_sessionOutput.tryEmit(output)
			}
		}
		logger.info("Session initialized  sessionId={}  workspace={}", _data.value.id, workspace.displayName)
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
	
	//endregion
	
	fun dispatch(command: AgentCommand) {
		val target = agent ?: error("No agent created")
		target.dispatch(command)
	}
	
	suspend fun updateConfig(config: SessionConfig) {
		logger.info("Session config updated  sessionId={}", _data.value.id)
		_data.update { it.copy(config = config) }
		dispatch(
			AgentCommand.Directive.UpdateModel(
				model = resolveModel(config.model),
				fallbackModels = config.fallbackModel?.map { resolveModel(it) },
				thinking = config.thinking,
			)
		)
	}
	
	fun updateTitle(title: String) {
		_data.update { it.copy(title = title) }
	}
	
	suspend fun send(content: String, images: List<Base64>? = null) {
		val agent = agent ?: return
		agent.statusFlow.first { it == AgentStatus.FREE }
		logger.info("Sent user message  sessionId={}  charCount={}", _data.value.id, content.length)
		
		val id = UUID.randomUUID()
		val timestamp = Clock.System.now()
		val userMsg = SessionMessage.User(id = id, timestamp = timestamp, content = content, images = images)
		messages[id] = userMsg
		
		dispatch(AgentCommand.Message.SendMessage(id = id, content = content, images = images, timestamp = timestamp))
		
		saveMessages(newMessages = listOf(userMsg))
		val currentCtx = _context.value
		updateContext(
			currentCtx.copy(
				index = currentCtx.index.copy(
					currentRound = CurrentRound(
						userMessage = userMsg.id, turns = null, assistantMessage = null, pendingToolCalls = null
					),
				)
			)
		)
	}
	
	suspend fun stop() {
		val agent = agent ?: return
		logger.info("Session stop initiated  sessionId={}", _data.value.id)
		dispatch(AgentCommand.Directive.Stop)
		agent.statusFlow.first { it == AgentStatus.FREE }
		save()
		logger.info("Session stopped  sessionId={}", _data.value.id)
	}
	
	
	private suspend fun syncContext(ctx: AgentContext) {
		val oldCtx = _context.value
		val result = AgentContextConverter.sync(ctx, oldCtx)
		
		saveMessages(result.messages)
		updateContext(
			SessionContext(
				systemPrompt = oldCtx.systemPrompt, index = result.index, droppedMessages = result.droppedMessageIds
			)
		)
	}
	
	private suspend fun processAgentOutput(output: AgentOutput): SessionOutput? = when (output) {
		is AgentOutput.LlmDelta -> SessionOutput.LlmDelta(output.delta)
		is AgentOutput.LlmError -> SessionOutput.LlmError(
			output.error.content, output.error.statusCode, output.error.model, output.error.timestamp
		)
		
		is AgentOutput.Compact -> SessionOutput.Compact(output.output)
		is AgentOutput.Error -> SessionOutput.Error(output.error)
		is AgentOutput.Tool -> SessionOutput.Tool(output.output)
		is AgentOutput.ToolRequest -> SessionOutput.ToolRequest(output.requests)
		is AgentOutput.UsageConsumed -> {
			val record = SessionMessage.UsageRecord(
				id = UUID.randomUUID(),
				timestamp = output.timestamp,
				snapshot = UsageSnapshot(output.usage, output.modelInfo),
			)
			messages[record.id] = record
			save()
			null
		}
		
		else -> null
	}
	
	private fun toAgentContext(): AgentContext {
		return SessionContextConverter.toAgentContext(
			context = _context.value, messages = messages.values.toList(), maxCompactedRounds = maxCompactedRounds
		)
	}
	
	private suspend fun save() {
		store.saveContext(_data.value.id, _context.value)
		if (messages.isNotEmpty()) {
			store.saveMessages(messages.values.toList())
		}
	}
	
	private suspend fun updateContext(context: SessionContext) {
		_context.update { context }
		save()
	}
	
	private suspend fun saveMessages(newMessages: List<SessionMessage>) {
		newMessages.forEach { messages[it.id] = it }
		UsageStore.collect(newMessages)
		save()
	}
	
	private suspend fun createAgent() {
		val fallbackModels = _data.value.config.fallbackModel?.map { resolveModel(it) }
		val newAgent = Agent(
			context = toAgentContext(),
			workspace = workspace,
			model = resolveModel(_data.value.config.model),
			fallbackModels = fallbackModels?.ifEmpty { null },
			thinking = _data.value.config.thinking,
			summarizeModel = resolveModel(_data.value.config.summarizeModel),
			containerConfig = containerConfig,
			service = service,
			secretStore = secretStore,
			tools = _tools,
		)
		agents[newAgent.agentId] = newAgent
	}
}
