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

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.api.types.settings.find
import io.github.autotweaker.core.agent.AgentCommand
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.container.ContainerManager
import io.github.autotweaker.core.data.WorkspaceManager
import io.github.autotweaker.core.data.settings.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.slf4j.LoggerFactory
import java.util.*

object SessionManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val settings = Settings.get()
	
	private val defaultModelId: String = settings.find("core.session.model.default")
	private val systemPrompt: String = settings.find("core.session.system.prompt")
	
	private val store =
		ServiceLoader.load(SessionStore::class.java).firstOrNull() ?: error("No SessionStore implementation found")
	private val wsm = WorkspaceManager
	
	private val resolveModel: (ModelId) -> Model = { id ->
		ProviderService.getModel(id) ?: error("Unknown model: $id")
	}
	
	private val defaultModel: Model by lazy {
		ModelId.fromString(defaultModelId)?.let { ProviderService.getModel(it) }
			?: error("Cannot resolve default model")
	}
	
	private val sessions: MutableMap<UUID, Session> = mutableMapOf()
	
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val dataJobs: MutableMap<UUID, Job> = mutableMapOf()
	
	private fun startMonitor(session: Session) {
		val id = session.data.value.id
		dataJobs[id] = scope.launch {
			session.data.collectLatest { store.saveSessions(listOf(it)) }
		}
	}
	
	suspend fun send(session: UUID, content: String, images: List<Base64>? = null) {
		val session = if (sessions[session] == null) sessions[session]
		else {
			restore(session)
			sessions[session]
		}
		session!!.send(content, images)
	}
	
	suspend fun stop(session: UUID) = sessions[session]?.stop()
	
	fun pauseAgent(session: UUID) = sessions[session]?.dispatch(AgentCommand.Directive.Pause)
	
	fun resumeAgent(session: UUID) = sessions[session]?.dispatch(AgentCommand.Directive.Resume)
	
	fun cancelAgent(session: UUID) = sessions[session]?.dispatch(AgentCommand.Directive.Cancel)
	
	fun retryAgent(session: UUID) = sessions[session]?.dispatch(AgentCommand.Directive.Retry)
	
	fun compactAgent(session: UUID) = sessions[session]?.dispatch(AgentCommand.Directive.Compact)
	
	fun approveToolCall(session: UUID, approvals: List<ToolApprove>) {
		sessions[session]?.dispatch(AgentCommand.Message.ApproveToolCall(approvals))
	}
	
	fun get(id: UUID): SessionHandle? = sessions[id]?.let { getHandle(it) }
	
	suspend fun shutdown() {
		sessions.keys.toList().forEach { id ->
			runCatching { stop(id) }
		}
		scope.cancel()
	}
	
	suspend fun delete(sessionId: UUID) {
		val session = sessions[sessionId] ?: error("Session not found: $sessionId")
		session.stop()
		sessions.remove(sessionId)
		store.deleteSessions(listOf(sessionId))
		dataJobs[sessionId]?.cancel()
	}
	
	fun updateTitle(sessionId: UUID, title: String) {
		sessions[sessionId]?.updateTitle(title)
	}
	
	fun updateConfig(session: UUID, config: SessionConfig) = sessions[session]?.updateConfig(config)
	
	suspend fun create(config: SessionConfig): SessionHandle {
		val ws = wsm.getOrCreateDefault()
		return create(ws.id, config)
	}
	
	suspend fun create(workspaceId: UUID, config: SessionConfig): SessionHandle {
		val data = wsm.getData(workspaceId) ?: error("Workspace not found: $workspaceId")
		if (data.meta.inContainer && !ContainerManager.isRunning) ContainerManager.start()
		val session = Session(
			config = config,
			context = SessionContext.emptyContext(systemPrompt),
			store = store,
			resolveModel = resolveModel,
			defaultModel = defaultModel,
			workspaceId = data.id,
			workspace = data.meta,
			containerConfig = ContainerConfig(),
			settings = settings,
		)
		sessions[session.data.value.id] = session
		startMonitor(session)
		wsm.updateData(
			id = data.id, git = data.git, sessionIds = data.sessionIds.orEmpty() + session.data.value.id
		)
		store.saveSessions(listOf(session.data.value))
		logger.info("Session created  sessionId={}  workspaceId={}", session.data.value.id, data.id)
		return getHandle(session)
	}
	
	internal suspend fun updateWorkspaceName(id: UUID, new: String) = store.loadAllSessions()?.forEach {
		if (it.workspaceId == id) {
			sessions[it.id]?.updateWorkspaceName(new)
		}
	}
	
	suspend fun loadData(ids: List<UUID>) = store.loadSessions(ids)
	
	suspend fun loadMessages(ids: List<UUID>) = store.loadMessages(ids)
	
	suspend fun loadContext(sessionId: UUID) = store.loadContext(sessionId)
	
	private suspend fun restore(id: UUID): SessionHandle {
		val data = store.loadSessions(listOf(id))?.first() ?: error("$id not found")
		val context = store.loadContext(data.id) ?: SessionContext.emptyContext(systemPrompt)
		val workspaceId = data.workspaceId
		val session = Session(
			config = data.config,
			context = context,
			store = store,
			resolveModel = resolveModel,
			defaultModel = defaultModel,
			workspaceId = workspaceId,
			workspace = wsm.getData(workspaceId)?.meta ?: error("Workspace not found: $workspaceId"),
			settings = settings,
			containerConfig = ContainerConfig(),
		)
		sessions[session.data.value.id] = session
		startMonitor(session)
		return getHandle(session)
	}
	
	private fun getHandle(session: Session): SessionHandle = SessionHandle(
		id = session.data.value.id,
		context = session.context,
		output = session.output,
		status = session.agentStatus,
		data = session.data,
	)
}
