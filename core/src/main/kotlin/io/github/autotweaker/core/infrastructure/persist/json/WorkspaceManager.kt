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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.CONFIG_PATH
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.serializer.MutableListSerializer
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import java.nio.file.Files
import java.util.*

object WorkspaceManager : MutableStore<MutableList<WorkspaceData>>(), Loggable {
	override val serializer = MutableListSerializer(WorkspaceData.serializer())
	override fun default() = mutableListOf<WorkspaceData>()
	
	@Volatile
	private var defaultCreated = false
	
	suspend fun updateMeta(meta: WorkspaceMeta) = transform { workspaces ->
		require(meta.id != defaultWorkspaceId)
		check(meta.id in workspaces.map { it.meta.id })
		updateWorkspace(meta.id) { copy(meta = meta) }
		log.debug("Updated workspace meta  id={}", meta.id)
	}
	
	suspend fun updateSessions(id: UUID, sessionIds: List<UUID>?) = ensureAndTransform { workspaces ->
		check(id in workspaces.map { it.meta.id })
		updateWorkspace(id) { copy(sessionIds = sessionIds) }
		log.debug("Updated workspace data  id={}", id)
	}
	
	suspend fun delete(id: UUID): Boolean = ensureAndTransform { workspaces ->
		require(id != defaultWorkspaceId) { "Cannot delete default workspace" }
		workspaces.removeAll { it.meta.id == id }
	}.andLog(log) { info("Deleted workspace  id={}", id) }
	
	
	suspend fun create(meta: WorkspaceMeta): WorkspaceData = ensureAndTransform { workspaces ->
		check(workspaces.all { it.meta.id != meta.id }) { "Workspace ${meta.id} already exists" }
		WorkspaceData(meta = meta).also { workspaces.add(it) }
	}.andLog(log) { info("Created workspace  id={}  name={}", it.meta.id, it.meta.displayName) }
	
	suspend fun getData(id: UUID): WorkspaceData? = ensureAndTransform { workspaces ->
		workspaces.find { it.meta.id == id }
	}
	
	suspend fun getAll(): List<WorkspaceData> = ensureAndTransform {
		it.toList()
	}
	
	suspend fun <R> ensureAndTransform(block: suspend (MutableList<WorkspaceData>) -> R): R {
		ensureDefault()
		return transform(block)
	}
	
	private suspend fun ensureDefault() {
		if (!defaultCreated) {
			defaultCreated = true
			
			transform { workspaces ->
				val defaultPath = CONFIG_PATH.resolve("workspace")
				Files.createDirectories(defaultPath)
				
				val meta = WorkspaceMeta(
					id = defaultWorkspaceId, displayName = DEFAULT_WORKSPACE_NAME, path = defaultPath
				)
				val data = WorkspaceData(meta = meta)
				
				workspaces.add(data)
				
				log.info("Created default workspace  id={}  path={}", data.meta.id, data.meta.path)
			}
		}
	}
	
	private suspend fun updateWorkspace(
		id: UUID, transform: WorkspaceData.() -> WorkspaceData
	) = transform { list ->
		val index = list.indexOfFirst { it.meta.id == id }
		list[index] = list[index].transform()
	}
	
	private const val DEFAULT_WORKSPACE_NAME = "default"
	val defaultWorkspaceId: UUID = UUID.nameUUIDFromBytes(DEFAULT_WORKSPACE_NAME.toByteArray())
}
