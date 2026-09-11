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
import io.github.autotweaker.api.adapter.Session
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.addChild
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.findChildren
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.exception.notfound.AgentNotFoundException
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.domain.agent.AgentDeps
import io.github.autotweaker.core.domain.agent.AgentImpl
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class SessionImpl(
	initialData: SessionData,
	private val deps: AgentDeps,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val resolveModel: suspend (UUID) -> RuntimeModel,
	override val workspaceId: UUID,
	private val workspacePath: Path,
) : Session, Loggable, Traceable {
	override val id = initialData.id
	
	private val _agentIndex = MutableStateFlow(initialData.agentIndex)
	override val agentIndex = _agentIndex.asStateFlow()
	
	private val _title = MutableStateFlow(initialData.title)
	override val title = _title.asStateFlow()
	
	private val _overview = MutableStateFlow(initialData.overview)
	override val overview = _overview.asStateFlow()
	
	override val creationTime = initialData.creationTime
	
	val data: SessionData
		get() = SessionData(
			id = id,
			title = _title.value,
			overview = _overview.value,
			workspaceId = workspaceId,
			creationTime = creationTime,
			lastAccessTime = now(),
			agentIndex = _agentIndex.value,
		)
	
	private val lock = ReentrantMutex()
	private val scope = scope()
	private val bridges = ConcurrentHashMap<UUID, AgentBridge>()
	
	override fun getOrNull(agent: UUID) = bridges[agent]
	
	override suspend fun restore(agent: UUID): Agent = getOrRestore(agent)
		?: throw AgentNotFoundException(agent, id)
	
	suspend fun init(init: SessionInit) = also {
		val mainId = _agentIndex.value.main.id
		val main = lock.withLock {
			when (init) {
				is SessionInit.Restore -> restoreOrNull(mainId)
					?: throw AgentNotFoundException("Main agent not found for session '$id'", mainId, id)
						.andLog(log) {
							warn(
								"Main agent not found while restoring session  sessionId={}  agentId={}",
								it.sessionId, it.id
							)
						}
				
				is SessionInit.New -> newAgent(
					agentId = mainId,
					name = MAIN_AGENT_NAME.toKebab(),
					systemPrompt = init.systemPrompt,
					model = init.model,
				).andLog(log) {
					info(
						"Initialized session  sessionId={}  path={}",
						it.id,
						workspacePath
					)
				}
			}
		}
		val titleJob = if (_title.value == null) scope.launch {
			val lock = ReentrantMutex()
			
			suspend fun generateTitle(trigger: String): Boolean {
				if (main.context.value.index.ids().isEmpty()) return false
				val newTitle = main.title()
				if (newTitle == null) {
					log.warn("Failed to generate session title  sessionId={}  trigger={}", id, trigger)
					return false
				}
				updateTitle { it ?: newTitle }
				log.info("Generated session title  sessionId={}  trigger={}  title={}", id, trigger, newTitle)
				return true
			}
			
			val collectJob = launch {
				val messageCount = AutoTitleMessageCount().get()
				if (messageCount > 0)
					main.context.collect { ctx ->
						if (_title.value != null) throw CancellationException()
						if (ctx.index.ids().count() >= messageCount)
							lock.withLock {
								if (_title.value != null) throw CancellationException()
								if (!generateTitle("messageCount")) throw CancellationException()
							}
					}
			}
			val duration = AutoTitleDelaySeconds().get().let {
				if (it > 0) it else return@launch
			}.seconds
			delay(duration)
			lock.withLock {
				if (_title.value != null) throw CancellationException()
				if (!generateTitle("timeout")) return@withLock
				collectJob.cancel()
			}
		} else null
		scope.launch {
			main.status.collect {
				if (it.isDead) {
					titleJob?.cancel()
					throw CancellationException()
				}
			}
		}
		val messageCount = main.context.value.index.ids().count()
		val delta = OverviewMessageDelta().get()
		if (delta > 0) scope.launch {
			var lastCount = if (_overview.value == null) 0 else messageCount
			while (isActive) {
				val agent = bridges[mainId]
				if (agent == null || agent.isDead) {
					delay(5.seconds)
					continue
				}
				val collectJob = launch {
					log.debug("Started overview watcher  sessionId={}  agentId={}", id, agent.id)
					agent.context.collect { ctx ->
						val count = ctx.index.ids().count()
						if (count - lastCount >= delta) {
							lastCount = count
							val overview = agent.overview()
							if (overview == null) {
								log.warn("Failed to generate session overview  sessionId={}", id)
							} else {
								_overview.value = overview
								log.info("Generated session overview  sessionId={}", id)
								val cooldown = OverviewCooldownSeconds().get()
								if (cooldown > 0) delay(cooldown.seconds)
							}
						}
					}
				}
				
				agent.status.first { it.isDead }
				collectJob.cancelAndJoin()
			}
		}
	}
	
	sealed interface SessionInit {
		data class New(
			val model: ModelConfig,
			val systemPrompt: String
		) : SessionInit
		
		data object Restore : SessionInit
	}
	
	override fun updateTitle(function: (String?) -> String?) =
		_title.update { function(it) }
	
	suspend fun shutdown() = lock.withLock {
		bridges.values.forEachParallel {
			trace.catching { it.shutdown() }.onFailure { e ->
				log.warn("Failed agent shutdown  sessionId={}  reason={}", it.id, e.message)
			}
		}
	}
	
	private fun getHost(agentId: UUID) = object : AgentHost {
		override suspend fun create(name: KebabCase, systemPrompt: String, model: ModelConfig): AgentImpl =
			lock.withLock {
				val childId = UUID()
				_agentIndex.update { it.addChild(agentId, childId) }
				val bridge = newAgent(childId, name, systemPrompt, model)
				log.info("Created child agent  parentId={}  childId={}", agentId, childId)
				return@withLock bridge.agent
			}
		
		override fun list(): List<UUID> {
			val children = _agentIndex.value.findChildren(agentId)
			return children.map { it.id }
		}
		
		override suspend fun get(id: UUID): AgentImpl? = getOrRestore(id)?.agent
	}
	
	private suspend fun getOrRestore(id: UUID): AgentBridge? = lock.withLock {
		bridges[id] ?: restoreOrNull(id)
	}
	
	private suspend fun restoreOrNull(id: UUID): AgentBridge? = lock.withLock {
		sessionRepo.loadAgent(id)?.let {
			restoreAgent(it)
		}
	}
	
	private suspend fun newAgent(
		agentId: UUID,
		name: KebabCase,
		systemPrompt: String,
		model: ModelConfig,
	): AgentBridge = restoreAgent(
		AgentData(
			id = agentId,
			name = name,
			sessionId = id,
			creationTime = now(),
			lastAccessTime = now(),
			model = model,
			context = AgentContext.emptyContext(systemPrompt),
			activeTools = initialActiveTools()
		)
	)
	
	private suspend fun restoreAgent(
		data: AgentData,
	) = AgentBridge(
		deps = deps,
		host = getHost(data.id),
		onShutdown = { bridges.remove(data.id) },
		sessionRepo = sessionRepo,
		usageRepo = usageRepo,
		resolveModel = resolveModel,
		workspace = workspacePath,
		initialData = data
	).init().also { bridges[data.id] = it }
	
	private fun initialActiveTools() =
		InitialActiveTools().get()
			.split(',')
			.mapNotNullTo(mutableSetOf()) {
				it.trim().orNull()
			}
	
	@AutoService(SettingDef::class)
	class InitialActiveTools : StringSetting(
		"bash,read",
		zh("配置在新的Agent创建时就激活的工具，英文逗号分隔")
	)
	
	@AutoService(SettingDef::class)
	class AutoTitleMessageCount : IntSetting(
		5,
		zh("无标题会话在指定的消息数量后自动生成标题，与等待时间设置共同生效")
	)
	
	@AutoService(SettingDef::class)
	class AutoTitleDelaySeconds : IntSetting(
		60,
		zh("无标题会话在创建后等待多少秒自动生成标题，与消息条目设置共同生效")
	)
	
	@AutoService(SettingDef::class)
	class OverviewMessageDelta : IntSetting(
		20,
		zh("会话每新增此数量的消息后自动更新概述")
	)
	
	@AutoService(SettingDef::class)
	class OverviewCooldownSeconds : IntSetting(
		300,
		zh("更新会话概述后指定秒内不再重新生成")
	)
	
	companion object {
		const val MAIN_AGENT_NAME = "main"
	}
}
