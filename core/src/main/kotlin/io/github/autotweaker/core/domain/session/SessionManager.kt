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
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.getAll
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import io.github.autotweaker.core.infrastructure.persistence.WorkspaceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionManager : Loggable, Traceable, Settable {
	private val systemPrompt = setting.get(SystemPrompt()).value
	
	private val wsm = WorkspaceManager
	
	private lateinit var store: SessionRepository
	private lateinit var modelRepo: ModelRepository
	private lateinit var secretStore: SecretStore
	
	fun init(store: SessionRepository, modelRepo: ModelRepository, secretStore: SecretStore) {
		this.store = store
		this.modelRepo = modelRepo
		this.secretStore = secretStore
	}
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	
	private val sessions = ConcurrentHashMap<UUID, Session>()
	private val listener = ConcurrentHashMap<UUID, Job>()
	
	
	suspend fun shutdown() {
		log.info("Initiated SessionManager shutdown  activeSessions={}", sessions.size)
		coroutineScope {
			sessions.forEach { (id, session) ->
				launch {
					trace.catching { session.shutdown() }.onFailure { e ->
						log.warn("Failed to shutdown session  sessionId={}  reason={}", id, e.message)
					}
				}
			}
		}
		scope.cancel()
		log.info("Completed SessionManager shutdown")
	}
	
	fun isContainerRunning() = ContainerManager.isRunning
	
	suspend fun get(id: UUID): SessionHandle = getOrRestore(id).toHandle()
	
	suspend fun delete(id: UUID): Boolean {
		val data = store.loadSessions(listOf(id)).firstOrNull() ?: return false
		sessions[id]?.shutdown()
		listener[id]?.cancel()
		sessions.remove(id)
		store.deleteSessions(listOf(id))
		data.agentIndex.getAll().forEach { store.deleteAgent(it) }
		log.info("Deleted session  id={}", id)
		return true
	}
	
	suspend fun updateTitle(session: UUID, title: String) =
		getOrRestore(session).updateTitle(title).andLog(log)
		{ debug("Updated session title  session={}  title={}", session, title) }.discard()
	
	
	suspend fun create(model: ModelConfig) = create(wsm.getDefault().meta.id, model)
	
	suspend fun loadData(ids: List<UUID>) = store.loadSessions(ids)
	suspend fun loadMessages(ids: List<UUID>) = store.loadMessages(ids)
	suspend fun loadAgent(id: UUID) = store.loadAgent(id)
	
	suspend fun create(workspaceId: UUID, model: ModelConfig): UUID {
		val workspaceData = wsm.getData(workspaceId) ?: error("Workspace not found: $workspaceId")
		if (!Files.isDirectory(workspaceData.meta.path)) {
			error("Workspace directory does not exist: ${workspaceData.meta.path}")
		}
		val data = SessionData(
			id = UUID.randomUUID(),
			title = null,
			overview = null,
			model = model,
			workspaceId = workspaceId,
			agentIndex = AgentIndex.emptyIndex()
		)
		sessions[data.id] = Session(
			data = data,
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspaceData.meta,
			secretStore = secretStore,
		).init(
			systemPrompt = systemPrompt,
			activeTools = emptyList()
		).listen().andSave()
		wsm.updateSessions(
			workspaceData.meta.id, sessionIds = workspaceData.sessionIds.orEmpty() + data.id
		)
		log.info("Created session  sessionId={}  workspaceId={}", data.id, workspaceData.meta.id)
		return data.id
	}
	
	private suspend fun getOrRestore(id: UUID): Session = sessions[id] ?: restore(id)
	
	private suspend fun restore(id: UUID): Session {
		val data = store.loadSessions(listOf(id)).firstOrNull() ?: error("Session not found: $id")
		val workspaceId = data.workspaceId
		val workspaceMeta = wsm.getData(workspaceId)?.meta ?: error("Workspace not found: $workspaceId")
		if (!Files.isDirectory(workspaceMeta.path)) {
			error("Workspace directory does not exist: ${workspaceMeta.path}")
		}
		return Session(
			data = data,
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspaceMeta,
			secretStore = secretStore,
		).init(
			systemPrompt = systemPrompt,
			activeTools = emptyList()
		)
			.listen()
			.also { sessions[data.id] = it }
			.andLog(log)
			{ info("Restored session  sessionId={}  workspaceId={}", it.data.value.id, workspaceId) }
	}
	
	private fun Session.toHandle() = SessionHandle(
		data = data,
		agents = agents.values.toList()
	)
	
	private fun Session.listen(): Session = also {
		val id = data.value.id
		listener[id] = scope.launch {
			data.collectLatest {
				store.saveSessions(listOf(it))
			}
		}
	}
	
	private suspend fun Session.andSave(): Session = also {
		store.saveSessions(listOf(data.value))
	}
	
	private suspend fun resolveModel(id: UUID): Model =
		modelRepo.resolve(id) ?: error("Unknown model: $id")
	
	@AutoService(SettingDef::class)
	class SystemPrompt : SettingDef<SettingValue.ValString> {
		override val default by lazy { SettingValue.ValString(ResourcesLoader.loadPrompt("system")) }
		override val description = "系统提示词，作用于整个项目"
	}
}
