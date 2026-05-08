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

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.AgentCommand
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.AgentStatus
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.container.ContainerManager
import io.github.autotweaker.core.data.provider.ProviderManager
import io.github.autotweaker.core.data.session.SessionStoreImpl
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.data.settings.find
import io.github.autotweaker.core.session.workspace.WorkspaceManager
import io.github.autotweaker.core.session.workspace.WorkspaceMeta
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.*

object SessionManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val settings = Settings.get()
	
	private val defaultModelId: String = settings.find("core.session.model.default")
	private val systemPrompt: String = settings.find("core.session.system.prompt")
	
	private val store = SessionStoreImpl().also { it.init() }
	private val wsm = WorkspaceManager
	
	private val resolveModel: (ModelId) -> Model = { id ->
		ProviderManager.getModel(id) ?: error("Unknown model: $id")
	}
	
	private val defaultModel: Model by lazy {
		ModelId.fromString(defaultModelId)?.let { ProviderManager.getModel(it) }
			?: error("Cannot resolve default model")
	}
	
	object SessionAPI {
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
		
		fun pauseAgent(session: UUID) =
			sessions[session]?.dispatch(AgentCommand.Directive.Pause)
		
		fun resumeAgent(session: UUID) =
			sessions[session]?.dispatch(AgentCommand.Directive.Resume)
		
		fun cancelAgent(session: UUID) =
			sessions[session]?.dispatch(AgentCommand.Directive.Cancel)
		
		fun retryAgent(session: UUID) =
			sessions[session]?.dispatch(AgentCommand.Directive.Retry)
		
		fun compactAgent(session: UUID) =
			sessions[session]?.dispatch(AgentCommand.Directive.Compact)
		
		fun list(): List<SessionHandle> =
			sessions.map { entry -> SessionHandle.fromSession(entry.value) }
		
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
		
		suspend fun create(workspace: String, config: SessionConfig): SessionHandle {
			val workspace = getWorkspace(workspace)
			if (workspace.meta.inContainer && !ContainerManager.isRunning) ContainerManager.start()
			val session = Session(
				config = config,
				context = SessionContext.emptyContext(systemPrompt),
				store = store,
				resolveModel = resolveModel,
				defaultModel = defaultModel,
				workspace = workspace.meta,
				containerConfig = ContainerConfig(),
				settings = settings,
			)
			sessions[session.data.value.id] = session
			startMonitor(session)
			wsm.updateData(
				name = workspace.meta.name,
				git = workspace.git,
				sessionIds = workspace.sessionIds.orEmpty() + session.data.value.id
			)
			store.saveSessions(listOf(session.data.value))
			logger.info("Session created  sessionId={}  workspace={}", session.data.value.id, workspace.meta.name)
			return SessionHandle.fromSession(session)
		}
		
		internal suspend fun updateWorkspaceName(name: String, new: String) = store.loadAllSessions()?.forEach {
			if (it.workspaceName == name) {
				sessions[it.id]?.updateWorkspaceName(new)
			}
		}
		
		
		private suspend fun restore(id: UUID): SessionHandle {
			val data = store.loadSessions(listOf(id))?.first() ?: error("$id not found")
			val context = store.loadContext(data.id) ?: SessionContext.emptyContext(systemPrompt)
			val session = Session(
				config = data.config,
				context = context,
				store = store,
				resolveModel = resolveModel,
				defaultModel = defaultModel,
				workspace = getWorkspace(data.workspaceName!!).meta,
				settings = settings,
				containerConfig = ContainerConfig(),
			)
			sessions[session.data.value.id] = session
			startMonitor(session)
			return SessionHandle.fromSession(session)
		}
	}
	
	object WorkspaceAPI {
		fun create(meta: WorkspaceMeta) {
			if (!Files.isDirectory(meta.path)) error("${meta.path} is not a directory")
			wsm.create(meta)
			if (Files.isDirectory(meta.path.resolve(".git"))) {
				wsm.updateData(meta.name, git = true, null)
			}
		}
		
		suspend fun updateName(name: String, newName: String) {
			val oldMeta = getWorkspace(name).meta
			wsm.updateMeta(name = name, meta = oldMeta.copy(name = newName))
			SessionAPI.updateWorkspaceName(name, newName)
		}
		
		suspend fun delete(name: String) {
			getWorkspace(name).sessionIds?.forEach { SessionAPI.delete(it) }
			wsm.delete(name)
		}
		
		fun list() = wsm.getAll()
	}
	
	private fun getWorkspace(name: String) = wsm.getData(name) ?: error("Workspace not found: $name")
	
	data class SessionHandle(
		val id: UUID,
		val context: StateFlow<SessionContext>,
		val output: SharedFlow<AgentOutput>,
		val status: StateFlow<AgentStatus>,
		val data: StateFlow<SessionData>,
	) {
		companion object {
			fun fromSession(session: Session) = SessionHandle(
				id = session.data.value.id,
				context = session.context,
				output = session.output,
				status = session.agentStatus,
				data = session.data,
			)
		}
	}
}
