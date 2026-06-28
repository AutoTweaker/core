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

package io.github.autotweaker.core.infrastructure.persistence

import io.github.autotweaker.api.*
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.file.Files
import java.util.*

object WorkspaceManager : Loggable, JsonStorable {
	private val workspaces: MutableList<WorkspaceData> = mutableListOf()
	
	private val lock = ReentrantMutex()
	
	suspend fun init() = lock.withLock {
		store.get()?.let {
			workspaces.addAll(
				Json.decodeFromJsonElement<List<WorkspaceData>>(it)
			)
		}.andLog(log) { info("Initialized WorkspaceManager  count={}", workspaces.size) }
	}
	
	suspend fun updateMeta(meta: WorkspaceMeta) = lock.withLock {
		check(meta.id in workspaces.map { it.meta.id })
		update(meta.id) { copy(meta = meta) }
		log.debug("Updated workspace meta  id={}", meta.id)
	}
	
	suspend fun updateSessions(id: UUID, sessionIds: List<UUID>?) = lock.withLock {
		check(id in workspaces.map { it.meta.id })
		update(id) { copy(sessionIds = sessionIds) }
		log.debug("Updated workspace data  id={}", id)
	}
	
	suspend fun delete(id: UUID): Boolean = lock.withLock {
		require(id != defaultWorkspaceId) { "Cannot delete default workspace" }
		remove(id).andLog(log) { info("Deleted workspace  id={}", id) }
	}
	
	suspend fun getDefault(): WorkspaceData = lock.withLock {
		lookup(defaultWorkspaceId) ?: run {
			val defaultPath = CONFIG_PATH.resolve("workspace")
			Files.createDirectories(defaultPath)
			val meta = WorkspaceMeta(
				id = defaultWorkspaceId, displayName = DEFAULT_WORKSPACE_NAME, path = defaultPath
			)
			WorkspaceData(meta = meta).add()
				.andLog(log) { info("Created default workspace  id={}  path={}", it.meta.id, it.meta.path) }
		}
	}
	
	suspend fun create(meta: WorkspaceMeta): WorkspaceData = lock.withLock {
		if (workspaces.any { it.meta.id == meta.id }) error("Workspace ${meta.id} already exists")
		WorkspaceData(meta = meta).add()
			.andLog(log) { info("Created workspace  id={}  name={}", it.meta.id, it.meta.displayName) }
	}
	
	suspend fun getData(id: UUID): WorkspaceData? = lock.withLock { lookup(id) }
	
	suspend fun getAll(): List<WorkspaceData> = lock.withLock { workspaces.toList() }
	
	private fun lookup(id: UUID): WorkspaceData? = workspaces.find { it.meta.id == id }
	
	private fun update(id: UUID, transform: WorkspaceData.() -> WorkspaceData) {
		val index = workspaces.indexOfFirst { it.meta.id == id }
		workspaces[index] = workspaces[index].transform()
	}
	
	private fun remove(id: UUID): Boolean =
		workspaces.removeAll { it.meta.id == id }.andSave()
	
	private fun WorkspaceData.add(): WorkspaceData =
		also { workspaces.add(this).andSave() }
	
	private fun <T> T.andSave(): T =
		also { store.set(Json.encodeToJsonElement(workspaces)) }
	
	private const val DEFAULT_WORKSPACE_NAME = "default"
	val defaultWorkspaceId: UUID = UUID.nameUUIDFromBytes(DEFAULT_WORKSPACE_NAME.toByteArray())
}
