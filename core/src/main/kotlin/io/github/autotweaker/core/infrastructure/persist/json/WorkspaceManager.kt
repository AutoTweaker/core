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
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.DefaultWorkspaceMutationException
import io.github.autotweaker.api.types.exception.WorkspaceNotEmptyException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateWorkspaceIdException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateWorkspaceNameException
import io.github.autotweaker.api.types.exception.notfound.WorkspaceNotFoundException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import java.nio.file.Files
import java.util.*

object WorkspaceManager : MutableStore<MutableMap<UUID, WorkspaceData>>(), Loggable {
	override val serializer = MutableMapSerializer(
		UuidSerializer, WorkspaceData.serializer()
	)
	
	override fun default() = mutableMapOf<UUID, WorkspaceData>()
	
	suspend fun updateMeta(function: suspend () -> WorkspaceMeta) = transform { workspaces ->
		val meta = function()
		if (meta.id == defaultWorkspaceId) throw DefaultWorkspaceMutationException()
		ensureDefault()
		if (!workspaces.containsKey(meta.id)) throw WorkspaceNotFoundException(meta.id)
		if (workspaces.values.any { it.meta.displayName == meta.displayName })
			throw DuplicateWorkspaceNameException(meta.displayName)
		workspaces.computeIfPresent(meta.id) { _, old ->
			old.copy(meta = meta)
		}
		log.debug("Updated workspace meta  id={}", meta.id)
	}
	
	suspend fun updateSessions(id: UUID, function: (Set<UUID>) -> Set<UUID>) =
		transform { workspaces ->
			ensureDefault()
			workspaces.compute(id) { _, old ->
				val data = old ?: throw WorkspaceNotFoundException(id)
				data.copy(sessionIds = function(data.sessionIds))
			}
			log.debug("Updated workspace data  id={}", id)
		}
	
	suspend fun delete(id: UUID): Boolean = transform { workspaces ->
		if (id == defaultWorkspaceId)
			throw DefaultWorkspaceMutationException("Cannot delete default workspace")
		val data = workspaces[id] ?: return@transform false
		if (data.sessionIds.isEmpty()) {
			workspaces.remove(id)
			log.info("Deleted workspace  id={}", id)
			return@transform true
		} else throw WorkspaceNotEmptyException(id)
	}
	
	
	suspend fun create(meta: WorkspaceMeta): WorkspaceData = transform { workspaces ->
		ensureDefault()
		if (workspaces.containsKey(meta.id)) throw DuplicateWorkspaceIdException(meta.id)
		if (workspaces.values.any { it.meta.displayName == meta.displayName })
			throw DuplicateWorkspaceNameException(meta.displayName)
		WorkspaceData(meta = meta).also { workspaces[meta.id] = it }
	}
	
	suspend fun getData(id: UUID): WorkspaceData? = transform { workspaces ->
		ensureDefault()
		workspaces[id]
	}
	
	suspend fun getAll(): List<WorkspaceData> = transform {
		ensureDefault()
		it.values.toList()
	}
	
	private suspend fun ensureDefault() = transform { workspaces ->
		if (workspaces.containsKey(defaultWorkspaceId)) return@transform
		
		val defaultPath = CONFIG_PATH.resolve("workspace")
		Files.createDirectories(defaultPath)
		
		val meta = WorkspaceMeta(
			id = defaultWorkspaceId, displayName = DEFAULT_WORKSPACE_NAME, path = defaultPath
		)
		val data = WorkspaceData(meta = meta)
		
		workspaces[defaultWorkspaceId] = data
		
		log.info("Created default workspace  id={}  path={}", data.meta.id, data.meta.path)
	}
	
	private const val DEFAULT_WORKSPACE_NAME = "default"
	val defaultWorkspaceId: UUID = UUID.nameUUIDFromBytes(DEFAULT_WORKSPACE_NAME.toByteArray())
}
