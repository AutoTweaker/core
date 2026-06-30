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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.serializer.MutableListSerializer
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.infrastructure.persist.json.base.MutexStore
import java.nio.file.Files
import java.util.*

object WorkspaceManager : MutexStore<MutableList<WorkspaceData>>(), Loggable {
	override val serializer = MutableListSerializer(WorkspaceData.serializer())
	
	override fun default() = mutableListOf<WorkspaceData>()
	
	suspend fun init() = transform { workspaces ->
		workspaces.andLog(log) { info("Initialized WorkspaceManager  count={}", workspaces.size) }
	}
	
	suspend fun updateMeta(meta: WorkspaceMeta) = transform { workspaces ->
		check(meta.id in workspaces.map { it.meta.id })
		updateEntry(workspaces, meta.id) { copy(meta = meta) }
		log.debug("Updated workspace meta  id={}", meta.id)
	}
	
	suspend fun updateSessions(id: UUID, sessionIds: List<UUID>?) = transform { workspaces ->
		check(id in workspaces.map { it.meta.id })
		updateEntry(workspaces, id) { copy(sessionIds = sessionIds) }
		log.debug("Updated workspace data  id={}", id)
	}
	
	suspend fun delete(id: UUID): Boolean = transform { workspaces ->
		require(id != defaultWorkspaceId) { "Cannot delete default workspace" }
		workspaces.removeAll { it.meta.id == id }.andLog(log) { info("Deleted workspace  id={}", id) }
	}
	
	suspend fun getDefault(): WorkspaceData = transform { workspaces ->
		workspaces.find { it.meta.id == defaultWorkspaceId } ?: run {
			val defaultPath = CONFIG_PATH.resolve("workspace")
			Files.createDirectories(defaultPath)
			val meta = WorkspaceMeta(
				id = defaultWorkspaceId, displayName = DEFAULT_WORKSPACE_NAME, path = defaultPath
			)
			WorkspaceData(meta = meta).also { workspaces.add(it) }
				.andLog(log) { info("Created default workspace  id={}  path={}", it.meta.id, it.meta.path) }
		}
	}
	
	suspend fun create(meta: WorkspaceMeta): WorkspaceData = transform { workspaces ->
		if (workspaces.any { it.meta.id == meta.id }) error("Workspace ${meta.id} already exists")
		WorkspaceData(meta = meta).also { workspaces.add(it) }
			.andLog(log) { info("Created workspace  id={}  name={}", it.meta.id, it.meta.displayName) }
	}
	
	suspend fun getData(id: UUID): WorkspaceData? = transform { workspaces ->
		workspaces.find { it.meta.id == id }
	}
	
	suspend fun getAll(): List<WorkspaceData> = transform { it.toList() }
	
	private fun updateEntry(list: MutableList<WorkspaceData>, id: UUID, transform: WorkspaceData.() -> WorkspaceData) {
		val index = list.indexOfFirst { it.meta.id == id }
		list[index] = list[index].transform()
	}
	
	private const val DEFAULT_WORKSPACE_NAME = "default"
	val defaultWorkspaceId: UUID = UUID.nameUUIDFromBytes(DEFAULT_WORKSPACE_NAME.toByteArray())
}
