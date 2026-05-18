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

package io.github.autotweaker.core.session

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.api.types.session.SessionContextIndex.CurrentRound
import io.github.autotweaker.core.agent.Agent
import io.github.autotweaker.core.agent.AgentCommand
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.session.agent.AgentContextConverter
import io.github.autotweaker.core.session.agent.SessionContextConverter
import io.github.autotweaker.core.tool.Tool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock

class Session(
	config: SessionConfig,
	context: SessionContext,
	private val store: SessionStore,
	private val resolveModel: (UUID) -> Model,
	private val workspaceId: UUID,
	private var workspace: WorkspaceMeta,
	private val containerConfig: ContainerConfig,
	private val service: SettingService,
	private val maxCompactedRounds: Int = 0,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private val _tools: List<Tool> = ServiceLoader.load(Tool::class.java).toList()
	
	private val _data = MutableStateFlow(
		SessionData(
			id = UUID.randomUUID(),
			title = null,
			workspaceId = workspaceId,
			config = config,
		)
	)
	val data: StateFlow<SessionData> = _data.asStateFlow()
	
	private val _context = MutableStateFlow(context)
	val context: StateFlow<SessionContext> = _context.asStateFlow()
	
	private val messages = mutableMapOf<UUID, SessionMessage>()
	
	private val agents = mutableMapOf<UUID, Agent>()
	val agent: Agent? get() = agents.values.firstOrNull()
	
	private val _sessionOutput = MutableSharedFlow<SessionOutput>(
		extraBufferCapacity = 64
	)
	val output = _sessionOutput.asSharedFlow()
	
	val agentStatus: StateFlow<AgentStatus> get() = agent?.statusFlow ?: error("No agent created")
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	//初始化
	init {
		val ids = collectMessageIds(_context.value, maxCompactedRounds).toList()
		if (ids.isNotEmpty()) {
			val messages = runBlocking { store.loadMessages(ids) }
			messages?.forEach { this.messages[it.id] = it }
		}
		createAgent()
		logger.info("Session initialized  sessionId={}  workspace={}", _data.value.id, workspace.name)
		scope.launch {
			agent?.context?.collectLatest { syncContext(it) }
		}
		scope.launch {
			agent?.output?.collect {
				val output = processAgentOutput(it) ?: return@collect
				_sessionOutput.tryEmit(output)
			}
		}
	}
	
	//toAgentContext封装
	private fun toAgentContext(): AgentContext {
		return SessionContextConverter.toAgentContext(
			context = _context.value,
			messages = messages.values.toList(),
			resolveModel = resolveModel,
			maxCompactedRounds = maxCompactedRounds
		)
	}
	
	//保存上下文
	private suspend fun save() {
		store.saveContext(_data.value.id, _context.value)
		if (messages.isNotEmpty()) {
			store.saveMessages(messages.values.toList())
		}
	}
	
	//更新上下文索引
	private suspend fun updateContext(context: SessionContext) {
		_context.update { context }
		save()
	}
	
	//更新消息列表
	private suspend fun saveMessages(newMessages: List<SessionMessage>) {
		newMessages.forEach { messages[it.id] = it }
		save()
	}
	
	private fun createAgent() {
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
			tools = _tools,
		)
		agents[newAgent.agentId] = newAgent
	}
	
	fun dispatch(command: AgentCommand) {
		val target = agent ?: error("No agent created")
		target.dispatch(command)
	}
	
	fun updateConfig(config: SessionConfig) {
		_data.update { _data.value.copy(config = config) }
		dispatch(
			AgentCommand.Directive.UpdateModel(
				model = resolveModel(config.model),
				fallbackModels = config.fallbackModel?.map { resolveModel(it) },
				thinking = config.thinking,
			)
		)
	}
	
	fun updateTitle(title: String) {
		_data.update { _data.value.copy(title = title) }
	}
	
	fun updateWorkspaceName(name: String) {
		workspace = workspace.copy(name = name)
	}
	
	suspend fun send(content: String, images: List<Base64>? = null) {
		val agent = agent ?: return
		agent.statusFlow.first { it == AgentStatus.FREE }
		logger.info("Sent user message  sessionId={}  charCount={}", _data.value.id, content.length)
		
		val ctx = _context.value
		
		val id = UUID.randomUUID()
		val timestamp = Clock.System.now()
		val userMsg = SessionMessage.User(id = id, timestamp = timestamp, content = content, images = images)
		messages[id] = userMsg
		
		dispatch(AgentCommand.Message.SendMessage(id = id, content = content, images = images, timestamp = timestamp))
		
		saveMessages(newMessages = listOf(userMsg))
		updateContext(
			ctx.copy(
				index = ctx.index.copy(
					currentRound = CurrentRound(
						userMessage = userMsg.id, turns = null, assistantMessage = null, pendingToolCalls = null
					),
				)
			)
		)
	}
	
	suspend fun stop() {
		val agent = agent ?: return
		dispatch(AgentCommand.Directive.Stop)
		agent.statusFlow.first { it == AgentStatus.FREE }
		save()
		scope.cancel()
	}
	
	//更新SessionContext
	suspend fun syncContext(ctx: AgentContext) {
		val oldCtx = _context.value
		val result = AgentContextConverter.sync(ctx, oldCtx)
		
		saveMessages(result.messages)
		updateContext(
			SessionContext(
				systemPrompt = oldCtx.systemPrompt,
				usage = result.usage,
				index = result.index,
				droppedMessages = result.droppedMessageIds
			)
		)
	}
	
	//收集消息id
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
	
	private fun processAgentOutput(output: AgentOutput): SessionOutput? = when (output) {
		is AgentOutput.LlmDelta -> SessionOutput.LlmDelta(output.delta)
		is AgentOutput.LlmError -> SessionOutput.LlmError(
			output.error.content, output.error.statusCode, output.error.retrying?.id, output.error.timestamp
		)
		
		is AgentOutput.Compact -> SessionOutput.Compact(output.output)
		is AgentOutput.Error -> SessionOutput.Error(output.error)
		is AgentOutput.Tool -> SessionOutput.Tool(output.output)
		is AgentOutput.ToolRequest -> SessionOutput.ToolRequest(output.requests)
		else -> null
	}
}
