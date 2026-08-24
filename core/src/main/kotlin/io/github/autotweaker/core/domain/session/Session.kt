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
import io.github.autotweaker.core.domain.agent.Agent
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Session(
	data: SessionData,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private val workspace: Path,
) : Loggable {
	private val _data = MutableStateFlow(data)
	val data: StateFlow<SessionData> = _data.asStateFlow()
	
	private val index get() = _data.value.agentIndex
	
	private val lock = ReentrantMutex()
	private val bridges = ConcurrentHashMap<UUID, AgentBridge>()
	val agents: Map<UUID, AgentAPI> get() = bridges.toMap()
	
	suspend fun init(init: SessionInit) = also {
		lock.withLock {
			val mainId = index.main.id
			when (init) {
				is SessionInit.Restore -> restoreOrNull(mainId)
					?: throw AgentNotFoundException(mainId, _data.value.id).andLog(log) {
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
						activeTools = init.activeTools
					)
				).andLog(log) {
					info(
						"Initialized session  sessionId={}  path={}",
						it.id,
						workspace
					)
				}
			}
		}
	}
	
	sealed interface SessionInit {
		data class New(
			val model: ModelConfig,
			val systemPrompt: String,
			val activeTools: Set<String>
		) : SessionInit
		
		data object Restore : SessionInit
	}
	
	fun updateTitle(function: (String?) -> String?) = also {
		_data.update { it.copy(title = function(it.title)) }
	}
	
	suspend fun shutdown() = lock.withLock {
		bridges.values.forEachParallel { it.shutdown() }
	}
	
	private fun getHost(agentId: UUID) = object : AgentHost {
		override suspend fun create(name: KebabCase, systemPrompt: String, model: ModelConfig): Agent = lock.withLock {
			val childId = UUID()
			_data.update { it.copy(agentIndex = it.agentIndex.addChild(agentId, childId)) }
			val bridge = newAgent(childId, name, systemPrompt, model)
			log.info("Created child agent  parentId={}  childId={}", agentId, childId)
			return@withLock bridge.agent
		}
		
		override fun list(): List<UUID> {
			val children = index.findChildren(agentId)
			return children.map { it.id }
		}
		
		override suspend fun get(id: UUID): Agent? = getOrRestore(id)?.agent
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
			activeTools = emptySet()
		)
	)
	
	private suspend fun restoreAgent(
		data: AgentData,
	) = AgentBridge(
		host = getHost(data.id),
		onSend = onSendIfMain(data.id),
		sessionRepo = sessionRepo,
		usageRepo = usageRepo,
		resolveModel = resolveModel,
		workspace = workspace
	).init(data).also { bridges[data.id] = it }
	
	private fun onSendIfMain(id: UUID): ((MessageContent) -> Unit)? =
		if (id == index.main.id) {
			onSend@{
				if (_data.value.title != null) return@onSend
				val text = it.content?.filterIsInstance<ContentPart.Text>()?.firstOrNull()?.content
					?: return@onSend
				updateTitle { old ->
					old ?: text.lines().firstOrNull()?.take(20)
				}
			}
		} else null
	
	companion object {
		const val MAIN_AGENT_NAME = "main"
	}
}
