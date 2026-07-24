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
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.Agent
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Session(
	data: SessionData,
	private val store: SessionRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private val workspace: WorkspaceMeta,
) : Loggable, Settable {
	private val _data = MutableStateFlow(data)
	val data: StateFlow<SessionData> = _data.asStateFlow()
	
	private val index get() = _data.value.agentIndex
	
	private val lock = ReentrantMutex()
	private val bridges = ConcurrentHashMap<UUID, AgentBridge>()
	val agents: Map<UUID, AgentAPI> = bridges.toMap()
	
	suspend fun init(model: ModelConfig, systemPrompt: String, activeTools: Set<String>) = also {
		lock.withLock {
			val mainId = index.main.id
			restoreOrNull(mainId) ?: createAgent(
				AgentData(
					id = mainId,
					name = MAIN_AGENT_NAME.toKebab(),
					model = model,
					context = AgentContext.emptyContext(systemPrompt),
					activeTools = activeTools
				)
			).andLog(log) {
				info(
					"Initialized session  sessionId={}  workspace={}",
					it.id,
					workspace.displayName
				)
			}
		}
	}
	
	fun updateTitle(title: String) = also {
		_data.update { it.copy(title = title) }
	}
	
	suspend fun shutdown() = lock.withLock {
		bridges.values.forEachParallel { it.shutdown() }
	}
	
	private fun getHost(agentId: UUID) = object : AgentHost {
		override suspend fun create(name: KebabCase, systemPrompt: String, model: ModelConfig): Agent = lock.withLock {
			val childId = UUID.randomUUID()
			_data.update { it.copy(agentIndex = it.agentIndex.addChild(agentId, childId)) }
			val bridge = createAgent(childId, name, systemPrompt, model)
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
		val data: AgentData = store.loadAgent(id) ?: return@withLock null
		return@withLock createAgent(data)
	}
	
	private suspend fun createAgent(
		id: UUID,
		name: KebabCase,
		systemPrompt: String,
		model: ModelConfig,
	): AgentBridge = createAgent(
		AgentData(
			id = id,
			name = name,
			model = model,
			context = AgentContext.emptyContext(systemPrompt),
			activeTools = emptySet()
		)
	)
	
	private suspend fun createAgent(
		data: AgentData,
	) = AgentBridge(
		host = getHost(data.id),
		store = store,
		resolveModel = resolveModel,
		workspace = workspace
	).init(data).also { bridges[data.id] = it }
	
	companion object {
		const val MAIN_AGENT_NAME = "main"
	}
}
