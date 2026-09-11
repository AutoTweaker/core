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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.store.MutableStore
import io.github.autotweaker.api.types.exception.DefaultWorkspaceMutationException
import io.github.autotweaker.api.types.exception.InvalidWorkspacePathException
import io.github.autotweaker.api.types.exception.WorkspaceNotEmptyException
import io.github.autotweaker.api.types.exception.duplicate.DuplicateWorkspaceNameException
import io.github.autotweaker.api.types.exception.notfound.WorkspaceNotFoundException
import io.github.autotweaker.api.types.serializer.MutableMapSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.WorkspaceData
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object WorkspaceManager : MutableStore<MutableMap<UUID, WorkspaceData>>(), Loggable {
	override val serializer = MutableMapSerializer(
		UuidSerializer, WorkspaceData.serializer()
	)
	
	override fun default() = mutableMapOf<UUID, WorkspaceData>()
	
	suspend fun rename(id: UUID, newName: String) = transform { workspaces ->
		if (id == defaultWorkspaceId) throw DefaultWorkspaceMutationException()
		ensureDefault()
		if (!workspaces.containsKey(id)) throw WorkspaceNotFoundException(id)
		if (workspaces.values.any { it.displayName == newName })
			throw DuplicateWorkspaceNameException(newName)
		workspaces.computeIfPresent(id) { _, old ->
			old.copy(
				displayName = newName,
				lastAccessTime = now()
			)
		}
		log.info("Renamed workspace  id={}  newName={}", id, newName)
	}
	
	suspend fun touch(id: UUID) = transform { workspaces ->
		ensureDefault()
		if (!workspaces.containsKey(id)) throw WorkspaceNotFoundException(id)
		workspaces.computeIfPresent(id) { _, old ->
			old.copy(
				lastAccessTime = now()
			)
		}
	}
	
	suspend fun updateSessions(id: UUID, function: (Set<UUID>) -> Set<UUID>) =
		transform { workspaces ->
			ensureDefault()
			workspaces.compute(id) { _, old ->
				val data = old ?: throw WorkspaceNotFoundException(id)
				data.copy(
					sessionIds = function(data.sessionIds),
					lastAccessTime = now()
				)
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
	
	
	suspend fun create(displayName: String, path: Path): WorkspaceData = transform { workspaces ->
		ensureDefault()
		if (workspaces.values.any { it.displayName == displayName })
			throw DuplicateWorkspaceNameException(displayName)
		val resolved = HOME.resolve(path).normalize()
		if (!Files.isDirectory(resolved)) throw InvalidWorkspacePathException(resolved)
		WorkspaceData(path = resolved, displayName = displayName).also {
			workspaces[it.id] = it
			log.info("Created workspace  id={}  name={}  path={}", it.id, it.displayName, it.path)
		}
	}
	
	suspend fun getAndTouch(id: UUID): WorkspaceData? {
		touch(id)
		return getData(id)
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
		
		workspaces[defaultWorkspaceId] = WorkspaceData(
			id = defaultWorkspaceId,
			path = defaultPath,
			displayName = DEFAULT_WORKSPACE_NAME
		).andLog(log) {
			info("Created default workspace  id={}  path={}", it.id, it.path)
		}
	}
	
	private const val DEFAULT_WORKSPACE_NAME = "default"
	val defaultWorkspaceId: UUID = UUID.nameUUIDFromBytes(DEFAULT_WORKSPACE_NAME.toByteArray())
}
