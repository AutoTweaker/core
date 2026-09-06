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
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.addChild
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.findChildren
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.exception.notfound.AgentNotFoundException
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.domain.agent.AgentDeps
import io.github.autotweaker.core.domain.agent.AgentImpl
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SessionImpl(
	private val deps: AgentDeps,
	data: SessionData,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val resolveModel: suspend (UUID) -> RuntimeModel,
	override val workspaceId: UUID,
	private val workspacePath: Path,
) : Session, Loggable, Traceable {
	override val id = data.id
	
	private val _agentIndex = MutableStateFlow(data.agentIndex)
	override val agentIndex = _agentIndex.asStateFlow()
	
	private val _title = MutableStateFlow(data.title)
	override val title = _title.asStateFlow()
	
	private val _overview = MutableStateFlow(data.overview)
	override val overview = _overview.asStateFlow()
	
	val data: SessionData
		get() = SessionData(
			id = id,
			title = _title.value,
			overview = _overview.value,
			workspaceId = workspaceId,
			agentIndex = _agentIndex.value,
		)
	
	private val lock = ReentrantMutex()
	private val bridges = ConcurrentHashMap<UUID, AgentBridge>()
	
	override fun getOrNull(agent: UUID) = bridges[agent]
	
	override suspend fun restore(agent: UUID): Agent = getOrRestore(agent)
		?: throw AgentNotFoundException(agent, id)
	
	suspend fun init(init: SessionInit) = also {
		lock.withLock {
			val mainId = _agentIndex.value.main.id
			when (init) {
				is SessionInit.Restore -> restoreOrNull(mainId)
					?: throw AgentNotFoundException("Main agent not found for session '$id'", mainId, id)
						.andLog(log) {
							warn(
								"Main agent not found while restoring session  sessionId={}  agentId={}",
								it.sessionId, it.id
							)
						}
				
				is SessionInit.New -> restoreAgent(
					AgentData(
						id = mainId,
						name = MAIN_AGENT_NAME.toKebab(),
						model = init.model,
						context = AgentContext.emptyContext(init.systemPrompt),
						activeTools = initialActiveTools()
					)
				).andLog(log) {
					info(
						"Initialized session  sessionId={}  path={}",
						it.id,
						workspacePath
					)
				}
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
		id: UUID,
		name: KebabCase,
		systemPrompt: String,
		model: ModelConfig,
	): AgentBridge = restoreAgent(
		AgentData(
			id = id,
			name = name,
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
		onSend = onSendIfMain(data.id),
		onShutdown = { bridges.remove(data.id) },
		sessionRepo = sessionRepo,
		usageRepo = usageRepo,
		resolveModel = resolveModel,
		workspace = workspacePath
	).init(data).also { bridges[data.id] = it }
	
	private fun onSendIfMain(id: UUID): ((MessageContent) -> Unit)? =
		if (id == _agentIndex.value.main.id) {
			onSend@{
				if (_title.value != null) return@onSend
				val text = it.content?.filterIsInstance<ContentPart.Text>()?.firstOrNull()?.content
					?: return@onSend
				updateTitle { old ->
					old ?: text.lines().firstOrNull()?.take(100)
				}
			}
		} else null
	
	private fun initialActiveTools() =
		InitialActiveTools().get()
			.split(SPACE)
			.mapNotNullTo(mutableSetOf()) {
				it.ifBlank { null }
			}
	
	@AutoService(SettingDef::class)
	class InitialActiveTools : StringSetting(
		"bash read",
		zh("配置在新的Agent创建时就激活的工具，空格分隔")
	)
	
	companion object {
		const val MAIN_AGENT_NAME = "main"
	}
}
