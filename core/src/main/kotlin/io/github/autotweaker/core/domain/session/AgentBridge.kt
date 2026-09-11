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

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.Agent
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
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
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.domain.session.converter.AgentContextBuilder
import io.github.autotweaker.core.domain.session.converter.RuntimeContextBuilder
import io.github.autotweaker.core.domain.tool.CoreTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.util.*

class AgentBridge(
	private val deps: AgentDeps,
	private val host: AgentHost,
	private val onShutdown: () -> Unit,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val initialData: AgentData,
	private val resolveModel: suspend (UUID) -> RuntimeModel,
	workspace: Path,
) : Agent, Loggable, Traceable {
	/* 初始化 */
	private val contextLock = ReentrantMutex()
	
	private lateinit var tools: ToolMap
	
	private val _context = MutableStateFlow(initialData.context)
	override val context = _context.asStateFlow()
	private var droppedCompacted: AgentContextIndex.CompactedRounds? = null
	
	private var cwd = workspace
	
	private lateinit var _agent: AgentImpl
	val agent get() = _agent
	
	private val _output = MutableSharedFlow<AgentOutput>(
		extraBufferCapacity = 64
	)
	override val output: SharedFlow<AgentOutput> = _output.asSharedFlow()
	
	override val id = initialData.id
	override val sessionId = initialData.sessionId
	override val name: KebabCase get() = _agent.name
	override val status: StateFlow<AgentStatus> get() = _agent.status
	override val compacting: StateFlow<Boolean> get() = _agent.compacting
	override val activeTools: StateFlow<Set<String>> get() = _agent.activeTools
	override val toolCalling: StateFlow<Pair<String, ToolPresentation>?> get() = _agent.toolCalling
	
	override val model: ModelConfig get() = _agent.model
	
	private val agentData
		get() = AgentData(
			id = id,
			name = name,
			sessionId = sessionId,
			creationTime = initialData.creationTime,
			lastAccessTime = now(),
			model = _agent.model,
			context = _context.value,
			activeTools = activeTools.value
		)
	
	private val scope = scope()
	
	private val saveChannel = Channel<Unit>(Channel.CONFLATED)
	private var collectJob: Job? = null
	private var saveJob: Job? = null
	
	suspend fun init() = also {
		initTools(); createAgent()
		collectJob = scope.launch {
			_agent.context.collect { saveChannel.send(Unit) }
		}
		saveJob = scope.launch {
			saveChannel.consumeEach {
				trace.catching { _agent.context.value.save() }
					.onFailure { e ->
						log.error("Failed to save agent context  agentId={}", _agent.agentId, e)
					}
			}
		}
		scope.launch {
			_agent.activeTools.drop(1).collect {
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
		scope.launch {
			_agent.status.collect {
				when (it) {
					AgentStatus.FAILED -> scope.cancel("Agent failed", _agent.exception)
					AgentStatus.DEAD -> {
						collectJob?.cancelAndJoin()
						saveChannel.close()
						saveJob?.join()
						log.info("Agent died  agentId={}", _agent.agentId)
						scope().launch {
							scope.join()
							onShutdown()
						}
						scope.cancel()
					}
					
					else -> {}
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
	
	suspend fun shutdown() {
		_agent.shutdown()
		scope.join()
		log.info("Completed agent bridge shutdown  agentId={}", _agent.agentId)
	}
	
	suspend fun title() = summary(TitlePrompt().get(), "title")
	
	suspend fun overview() = summary(OverviewPrompt().get(), "overview")
	
	private suspend fun summary(prompt: String, key: String): String? {
		val result = _agent.summary(prompt) as? JsonObject ?: return null
		val primitive = result[key] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.content else null
	}
	
	override suspend fun send(content: MessageContent) =
		_agent.send(content).andLog(log) {
			info("Sent user message  agentId={}  contents={}", _agent.agentId, content.content?.count())
		}
	
	override suspend fun sendCoalescing(content: MessageContent) =
		_agent.sendCoalescing(content).andLog(log) {
			info("Queued coalescing message  agentId={}  contents={}", _agent.agentId, content.content?.count())
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
	
	private suspend fun RuntimeOutput.toSessionOutput(): AgentOutput? = when (this) {
		is RuntimeOutput.Output -> output
		is RuntimeOutput.UsageConsumed -> {
			val record = AgentMessage.UsageRecord(
				id = usage.id,
				timestamp = usage.timestamp,
				origin = setOf(id),
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
		_agent = AgentImpl(
			deps = deps,
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
		val builder = AgentContextBuilder(id, _context.value, this, droppedCompacted)
		val (context, messages) = builder()
		
		messages.save()
		updateContext {
			val droppedMessages = it.droppedMessages.orEmpty() + context.droppedMessages.orEmpty()
			context.copy(
				droppedMessages = droppedMessages.orNull()
			)
		}
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
		reasoning = reasoning
	)
	
	@AutoService(SettingDef::class)
	class OverviewPrompt : StringSetting(
		"你的任务是根据迄今为止的对话内容，输出一个会话概览，描述这个会话中发生了什么，不能超过200字。\n" +
				"你必须进行客观描述，禁止使用第一或第二人称。\n" +
				"你的输出必须是一个有效的json对象，包含一个overview字段，类型为字符串。\n" +
				"禁止在输出中包含markdown标记，如代码块：'```'。",
		zh("生成会话概览使用的提示词")
	)
	
	@AutoService(SettingDef::class)
	class TitlePrompt : StringSetting(
		"你的任务是根据迄今为止的对话内容，输出一个会话标题，简要地概括会话内容，不能超过30字。\n" +
				"你必须进行客观概括，禁止使用第一或第二人称。\n" +
				"你的输出必须是一个有效的json对象，包含一个title字段，类型为字符串。\n" +
				"禁止在输出中包含markdown标记，如代码块：'```'。",
		zh("生成会话标题使用的提示词")
	)
}
