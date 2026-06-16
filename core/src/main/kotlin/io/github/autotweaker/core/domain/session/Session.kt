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

import io.github.autotweaker.api.adapter.AgentAPI
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.KebabId
import io.github.autotweaker.api.types.KebabId.Companion.toKebabId
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.addChild
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.findChildren
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.Agent
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Session(
	data: SessionData,
	private val store: SessionRepository,
	private val resolveModel: suspend (UUID) -> Model,
	private val workspace: WorkspaceMeta,
	private val containerConfig: ContainerConfig,
	private val service: SettingService,
	private val secretStore: SecretStore,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private val _data = MutableStateFlow(data)
	val data: StateFlow<SessionData> = _data.asStateFlow()
	
	private val index get() = _data.value.agentIndex
	
	private val bridges = ConcurrentHashMap<UUID, AgentBridge>()
	val agents: Map<UUID, AgentAPI> = bridges.toMap()
	
	suspend fun init(systemPrompt: String, activeTools: List<String>) = also {
		val mainId = index.main.id
		restoreOrNull(mainId) ?: createAgent(
			AgentData(
				id = mainId,
				name = MAIN_AGENT_NAME.toKebabId(),
				model = _data.value.model,
				context = SessionContext.emptyContext(systemPrompt),
				activeTools = activeTools
			)
		).andLog(logger) {
			info(
				"Initialized session  sessionId={}  workspace={}",
				it.id,
				workspace.displayName
			)
		}
	}
	
	fun updateTitle(title: String) = also {
		_data.update { it.copy(title = title) }
	}
	
	suspend fun shutdown() = also {
		coroutineScope {
			bridges.values.forEach { bridge ->
				launch { bridge.shutdown() }
			}
		}
	}
	
	private fun getHost(agentId: UUID) = object : AgentHost {
		override suspend fun create(name: KebabId, systemPrompt: String, model: ModelConfig): Agent {
			val childId = UUID.randomUUID()
			_data.update { it.copy(agentIndex = it.agentIndex.addChild(agentId, childId)) }
			val bridge = createAgent(childId, name, systemPrompt, model)
			logger.info("Created child agent  parentId={}  childId={}", agentId, childId)
			return bridge.agent
		}
		
		override fun list(): List<UUID> {
			val children = index.findChildren(agentId)
			return children.map { it.id }
		}
		
		override suspend fun get(id: UUID): Agent? = getOrRestore(id)?.agent
	}
	
	private suspend fun getOrRestore(id: UUID): AgentBridge? =
		bridges[id] ?: restoreOrNull(id)
	
	private suspend fun restoreOrNull(id: UUID): AgentBridge? {
		val data: AgentData = store.loadAgent(id) ?: return null
		return createAgent(data)
	}
	
	private suspend fun createAgent(
		id: UUID,
		name: KebabId,
		systemPrompt: String,
		model: ModelConfig,
	): AgentBridge = createAgent(
		AgentData(
			id = id,
			name = name,
			model = model,
			context = SessionContext.emptyContext(systemPrompt),
			activeTools = emptyList()
		)
	)
	
	private suspend fun createAgent(
		data: AgentData,
	) = AgentBridge(
		host = getHost(data.id),
		store = store,
		resolveModel = resolveModel,
		workspace = workspace,
		containerConfig = containerConfig,
		service = service,
		secretStore = secretStore
	).init(data).also { bridges[data.id] = it }
	
	companion object {
		const val MAIN_AGENT_NAME = "main"
	}
}
