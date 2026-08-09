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
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.agent.AgentIndex.Companion.getAll
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.exception.*
import io.github.autotweaker.api.types.exception.notfound.*
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.data.PromptSetting
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionManager : Loggable, Traceable {
	private val systemPrompt = SystemPrompt().get()
	
	private val wsm = WorkspaceManager
	
	private lateinit var store: SessionRepository
	private lateinit var modelRepo: ModelResolver
	private lateinit var secretStore: SecretStore
	
	fun init(store: SessionRepository, modelRepo: ModelResolver, secretStore: SecretStore) {
		this.store = store
		this.modelRepo = modelRepo
		this.secretStore = secretStore
	}
	
	private val scope = scope()
	
	private val lock = ReentrantMutex()
	private val sessions = ConcurrentHashMap<UUID, Session>()
	private val listener = ConcurrentHashMap<UUID, Job>()
	
	
	suspend fun shutdown() = lock.withLock {
		log.info("Initiated SessionManager shutdown  activeSessions={}", sessions.size)
		sessions.entries.forEachParallel { (id, session) ->
			trace.catching { session.shutdown() }.onFailure { e ->
				log.warn("Failed session shutdown  sessionId={}  reason={}", id, e.message)
			}
		}
		scope.cancel()
		log.info("Completed SessionManager shutdown")
	}
	
	fun isContainerRunning() = ContainerManager.isRunning
	
	suspend fun get(id: UUID): SessionHandle = getOrRestore(id).toHandle()
	
	suspend fun delete(id: UUID): Boolean = lock.withLock {
		val data = store.loadSessions(setOf(id)).firstOrNull() ?: return@withLock false
		sessions[id]?.shutdown()
		listener[id]?.cancel()
		trace.catching { wsm.updateSessions(data.workspaceId) { it - id } }
			.recoverException { e: WorkspaceNotFoundException ->
				log.warn("Workspace not found while deleting session  sessionId={}  workspaceId={}", id, e.id)
			}.getOrThrow()
		sessions.remove(id)
		store.deleteSessions(setOf(id))
		data.agentIndex.getAll().forEach { store.deleteAgent(it) }
		log.info("Deleted session  id={}", id)
		return@withLock true
	}
	
	suspend fun updateTitle(session: UUID, title: String) =
		getOrRestore(session).updateTitle(title).andLog(log)
		{ debug("Updated session title  session={}  title={}", session, title) }.discard()
	
	
	suspend fun create(model: ModelConfig) = create(wsm.defaultWorkspaceId, model)
	
	suspend fun loadData(ids: Set<UUID>) = store.loadSessions(ids)
	suspend fun loadMessages(ids: Set<UUID>) = store.loadMessages(ids)
	suspend fun loadAgent(id: UUID) = store.loadAgent(id)
	
	suspend fun create(workspaceId: UUID, model: ModelConfig): UUID = lock.withLock {
		secretStore.requireUnlocked()
		val workspace = wsm.getData(workspaceId)?.meta?.path ?: throw WorkspaceNotFoundException(workspaceId)
		if (!Files.isDirectory(workspace)) throw InvalidWorkspacePathException(workspace)
		
		val data = SessionData(
			id = UUID.randomUUID(),
			title = null,
			overview = null,
			workspaceId = workspaceId,
			agentIndex = AgentIndex.new()
		)
		sessions[data.id] = Session(
			data = data,
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspace
		).init(
			Session.SessionInit.New(
				model = model,
				systemPrompt = systemPrompt,
				activeTools = emptySet()
			)
		).andSave().listen()
		trace.catching { wsm.updateSessions(workspaceId) { it + data.id } }
			.onException { e: WorkspaceNotFoundException ->
				sessions[data.id]?.shutdown()
				listener[data.id]?.cancel()
				sessions.remove(data.id)
				store.deleteSessions(setOf(data.id))
				data.agentIndex.getAll().forEach { store.deleteAgent(it) }
				log.warn(
					"Workspace deleted while creating session  sessionId={}  workspaceId={}",
					data.id, e.id
				)
			}.getOrThrow()
		log.info("Created session  sessionId={}  workspaceId={}", data.id, workspaceId)
		return@withLock data.id
	}
	
	private suspend fun Session.andSave(): Session = also {
		trace.catching { store.saveSessions(listOf(data.value)) }
			.onFailure {
				log.error("Failed to save session  sessionId={}", data.value.id, it)
				shutdown()
				store.deleteSessions(setOf(data.value.id))
				data.value.agentIndex.getAll().forEach { store.deleteAgent(it) }
			}.getOrThrow()
	}
	
	private suspend fun getOrRestore(id: UUID): Session = lock.withLock {
		sessions[id] ?: restore(id)
	}
	
	private suspend fun restore(id: UUID): Session = lock.withLock {
		secretStore.requireUnlocked()
		val data = store.loadSessions(setOf(id)).firstOrNull() ?: throw SessionNotFoundException(id)
		val workspaceId = data.workspaceId
		val workspace = wsm.getData(workspaceId)?.meta?.path ?: throw WorkspaceNotFoundException(workspaceId)
		if (!Files.isDirectory(workspace))
			throw InvalidWorkspacePathException(workspace)
		return@withLock Session(
			data = data,
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspace
		).init(Session.SessionInit.Restore)
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
	
	private suspend fun resolveModel(id: UUID): Model =
		modelRepo.resolve(id)
	
	@AutoService(SettingDef::class)
	class SystemPrompt : PromptSetting(
		"system", zh(
			"系统提示词，作用于整个项目"
		)
	)
}
