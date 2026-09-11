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
import io.github.autotweaker.api.adapter.Session
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.exception.InvalidWorkspacePathException
import io.github.autotweaker.api.types.exception.notfound.SessionNotFoundException
import io.github.autotweaker.api.types.exception.notfound.WorkspaceNotFoundException
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.domain.agent.AgentDeps
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.port.ModelResolver
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.infrastructure.data.PromptSetting
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
	private val agentDeps: AgentDeps,
	private val sessionRepo: SessionRepository,
	private val usageRepo: UsageRepository,
	private val modelRepo: ModelResolver,
	private val secretStore: SecretStore,
) : Loggable, Traceable {
	private val systemPrompt = SystemPrompt().get()
	
	private val wsm = WorkspaceManager
	
	private val scope = scope()
	
	private val lock = ReentrantMutex()
	private val sessions = ConcurrentHashMap<UUID, SessionImpl>()
	private val listener = ConcurrentHashMap<UUID, Job>()
	
	suspend fun shutdown() = lock.withLock {
		log.info("Initiated SessionManager shutdown  activeSessions={}", sessions.size)
		sessions.entries.forEachParallel { (id, session) ->
			trace.catching { session.shutdown() }.onFailure { e ->
				log.warn("Failed session shutdown  sessionId={}  reason={}", id, e.message)
			}
		}
		scope.cancelAndJoin()
		log.info("Completed SessionManager shutdown")
	}
	
	
	fun get(id: UUID): Session? = sessions[id]
	
	suspend fun delete(id: UUID): Boolean = lock.withLock {
		val data = sessionRepo.loadSession(id) ?: return@withLock false
		sessions[id]?.shutdown()
		listener[id]?.cancel()
		trace.catching { wsm.updateSessions(data.workspaceId) { it - id } }
			.recoverException { e: WorkspaceNotFoundException ->
				log.warn("Workspace not found while deleting session  sessionId={}  workspaceId={}", id, e.id)
			}.getOrThrow()
		sessions.remove(id)
		sessionRepo.deleteSessions(setOf(id))
		log.info("Deleted session  id={}", id)
		return@withLock true
	}
	
	suspend fun create(model: ModelConfig) = create(wsm.defaultWorkspaceId, model)
	
	suspend fun create(workspaceId: UUID, model: ModelConfig): UUID = lock.withLock {
		secretStore.requireUnlocked()
		val workspace = wsm.getData(workspaceId) ?: throw WorkspaceNotFoundException(workspaceId)
		if (!Files.isDirectory(workspace.path)) throw InvalidWorkspacePathException(workspace.path)
		
		val data = SessionData(
			id = UUID(),
			title = null,
			overview = null,
			workspaceId = workspaceId,
			creationTime = now(),
			lastAccessTime = now(),
			agentIndex = AgentIndex.new()
		)
		sessions[data.id] = SessionImpl(
			deps = agentDeps,
			initialData = data,
			sessionRepo = sessionRepo,
			usageRepo = usageRepo,
			resolveModel = ::resolveModel,
			workspaceId = workspace.id,
			workspacePath = workspace.path
		).init(
			SessionImpl.SessionInit.New(
				model = model,
				systemPrompt = systemPrompt
			)
		).andSave().listen()
		trace.catching { wsm.updateSessions(workspaceId) { it + data.id } }
			.onException { e: WorkspaceNotFoundException ->
				sessions[data.id]?.shutdown()
				listener[data.id]?.cancelAndJoin()
				sessions.remove(data.id)
				sessionRepo.deleteSessions(setOf(data.id))
				log.warn(
					"Workspace deleted while creating session  sessionId={}  workspaceId={}",
					data.id, e.id
				)
			}.getOrThrow()
		log.info("Created session  sessionId={}  workspaceId={}", data.id, workspaceId)
		return@withLock data.id
	}
	
	private suspend fun SessionImpl.andSave(): SessionImpl = also {
		trace.catching { sessionRepo.saveSessions(listOf(data)) }
			.onFailure { e ->
				log.error("Failed to save session  sessionId={}", id, e)
				shutdown()
				sessionRepo.deleteSessions(setOf(id))
			}.getOrThrow()
	}
	
	suspend fun getOrRestore(id: UUID): SessionImpl = lock.withLock {
		sessions[id] ?: restore(id)
	}
	
	private suspend fun restore(id: UUID): SessionImpl = lock.withLock {
		secretStore.requireUnlocked()
		val data = sessionRepo.loadSession(id) ?: throw SessionNotFoundException(id)
		val workspaceId = data.workspaceId
		val workspace = wsm.getData(workspaceId) ?: throw WorkspaceNotFoundException(workspaceId)
			.andLog(log) {
				warn(
					"Workspace not found while restoring session  sessionId={}  workspaceId={}",
					id, workspaceId
				)
			}
		if (!Files.isDirectory(workspace.path))
			throw InvalidWorkspacePathException(workspace.path).andLog(log) {
				warn(
					"Invalid workspace path while restoring session  sessionId={}  path={}",
					id, workspace.path
				)
			}
		
		return@withLock SessionImpl(
			deps = agentDeps,
			initialData = data,
			sessionRepo = sessionRepo,
			usageRepo = usageRepo,
			resolveModel = ::resolveModel,
			workspaceId = workspaceId,
			workspacePath = workspace.path
		).init(SessionImpl.SessionInit.Restore)
			.listen()
			.also { sessions[data.id] = it }
			.andLog(log) {
				info("Restored session  sessionId={}  workspaceId={}", it.id, workspaceId)
			}
	}
	
	private fun SessionImpl.listen(): SessionImpl = also {
		listener[id] = combine(
			agentIndex, title, overview
		) {
			sessionRepo.saveSessions(listOf(data))
		}.launchIn(scope)
	}
	
	private suspend fun resolveModel(id: UUID): RuntimeModel =
		modelRepo.resolve(id)
	
	@AutoService(SettingDef::class)
	class SystemPrompt : PromptSetting(
		"system", zh(
			"系统提示词，作用于整个项目"
		)
	)
}
