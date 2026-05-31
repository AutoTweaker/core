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

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.session.*
import io.github.autotweaker.core.domain.agent.AgentCommand
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.port.ModelRepository
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.persistence.WorkspaceManager
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object SessionManager {
	//region 初始化
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private val systemPrompt = Settings.get(SessionSettings.SystemPrompt()).value
	
	private val wsm = WorkspaceManager
	
	private lateinit var store: SessionRepository
	private lateinit var modelRepo: ModelRepository
	private lateinit var secretStore: SecretStore
	
	fun init(store: SessionRepository, modelRepo: ModelRepository, secretStore: SecretStore) {
		this.store = store
		this.modelRepo = modelRepo
		this.secretStore = secretStore
	}
	
	private suspend fun resolveModel(id: UUID): Model = modelRepo.resolve(id) ?: error("Unknown model: $id")
	
	private val sessions = ConcurrentHashMap<UUID, Session>()
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val dataJobs = ConcurrentHashMap<UUID, Job>()
	//endregion
	
	suspend fun send(session: UUID, content: String, images: List<Base64>? = null) {
		sessionOrRestore(session).send(content, images)
		logger.debug("Sent message  sessionId={}  charCount={}", session, content.length)
	}
	
	suspend fun stop(session: UUID) = sessionOrRestore(session).stop()
	
	suspend fun pauseAgent(session: UUID) = sessionOrRestore(session).dispatch(AgentCommand.Directive.Pause)
	
	suspend fun resumeAgent(session: UUID) = sessionOrRestore(session).dispatch(AgentCommand.Directive.Resume)
	
	suspend fun cancelAgent(session: UUID) = sessionOrRestore(session).dispatch(AgentCommand.Directive.Cancel)
	
	suspend fun retryAgent(session: UUID) = sessionOrRestore(session).dispatch(AgentCommand.Directive.Retry)
	
	suspend fun compactAgent(session: UUID) = sessionOrRestore(session).dispatch(AgentCommand.Directive.Compact)
	
	suspend fun approveToolCall(session: UUID, approvals: List<ToolApprove>) {
		sessionOrRestore(session).dispatch(AgentCommand.Message.ApproveToolCall(approvals))
	}
	
	suspend fun get(id: UUID): SessionHandle = getHandle(sessionOrRestore(id))
	
	suspend fun delete(id: UUID) {
		sessions[id]?.stop()
		dataJobs[id]?.cancel()
		sessions.remove(id)
		store.deleteSessions(listOf(id))
		logger.info("Deleted session  id={}", id)
	}
	
	suspend fun updateTitle(session: UUID, title: String) {
		sessionOrRestore(session).updateTitle(title)
		logger.debug("Updated session title  session={} title={}", session, title)
	}
	
	suspend fun updateConfig(session: UUID, config: SessionConfig) {
		sessionOrRestore(session).updateConfig(config)
		logger.debug("Updated session config  session={}", session)
	}
	
	suspend fun create(config: SessionConfig): UUID {
		val ws = wsm.getOrCreateDefault()
		return create(ws.meta.id, config)
	}
	
	suspend fun create(workspaceId: UUID, config: SessionConfig): UUID {
		val workspaceData = wsm.getData(workspaceId) ?: error("Workspace not found: $workspaceId")
		if (!Files.isDirectory(workspaceData.meta.path)) {
			error("Workspace directory does not exist: ${workspaceData.meta.path}")
		}
		val data = SessionData(id = UUID.randomUUID(), title = null, workspaceId = workspaceId, config = config)
		val session = Session(
			data = data,
			context = SessionContext.emptyContext(systemPrompt),
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspaceData.meta,
			containerConfig = ContainerConfig(),
			service = Settings,
			secretStore = secretStore,
		)
		session.init()
		sessions[session.data.value.id] = session
		startMonitor(session)
		wsm.updateData(
			id = workspaceData.meta.id, sessionIds = workspaceData.sessionIds.orEmpty() + session.data.value.id
		)
		store.saveSessions(listOf(session.data.value))
		logger.info("Session created  sessionId={} workspaceId={}", session.data.value.id, workspaceData.meta.id)
		return data.id
	}
	
	suspend fun loadData(ids: List<UUID>) = store.loadSessions(ids)
	suspend fun loadMessages(ids: List<UUID>) = store.loadMessages(ids)
	suspend fun loadContext(id: UUID) = store.loadContext(id)
	
	private suspend fun sessionOrRestore(id: UUID): Session = sessions[id] ?: restore(id)
	
	private suspend fun restore(id: UUID): Session {
		val data = store.loadSessions(listOf(id))?.first() ?: error("$id not found")
		val context = store.loadContext(data.id) ?: SessionContext.emptyContext(systemPrompt)
		val workspaceId = data.workspaceId
		val workspaceMeta = wsm.getData(workspaceId)?.meta ?: error("Workspace not found: $workspaceId")
		if (!Files.isDirectory(workspaceMeta.path)) {
			error("Workspace directory does not exist: ${workspaceMeta.path}")
		}
		val session = Session(
			data = data,
			context = context,
			store = store,
			resolveModel = ::resolveModel,
			workspace = workspaceMeta,
			service = Settings,
			containerConfig = ContainerConfig(),
			secretStore = secretStore,
		)
		session.init()
		sessions[session.data.value.id] = session
		startMonitor(session)
		logger.info("Restored session  sessionId={}  workspaceId={}", session.data.value.id, workspaceId)
		return session
	}
	
	fun isContainerRunning() = ContainerManager.isRunning
	
	private fun getHandle(session: Session): SessionHandle = SessionHandle(
		id = session.data.value.id,
		context = session.context,
		output = session.output,
		status = session.agentStatus,
		data = session.data,
	)
	
	private fun startMonitor(session: Session) {
		val id = session.data.value.id
		dataJobs[id] = scope.launch {
			session.data.collectLatest {
				store.saveSessions(listOf(it))
			}
		}
	}
	
	suspend fun shutdown() {
		logger.info("SessionManager shutdown initiated  activeSessions={}", sessions.size)
		sessions.keys.toList().forEach { id -> runCatching { stop(id) } }
		scope.cancel()
		logger.info("SessionManager shutdown completed")
	}
}
