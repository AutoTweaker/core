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

import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object WorkspaceManager {
	val DEFAULT_WORKSPACE_ID: UUID = UUID.nameUUIDFromBytes("autotweaker-default-workspace".toByteArray())
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(this::class)
	
	private var workspaceList: List<WorkspaceData>
	
	init {
		val jsonArray = jsonEntry.get()
		workspaceList = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement<List<WorkspaceData>>(jsonArray)
		logger.info("WorkspaceManager initialized  count={}", workspaceList.size)
	}
	
	fun create(meta: WorkspaceMeta): WorkspaceData {
		if (workspaceList.any { it.meta.name == meta.name }) error("Workspace with name ${meta.name} already exists")
		val data = WorkspaceData(meta = meta)
		update(workspaceList.plus(data))
		logger.debug("Workspace created  id={}  name={}", data.id, meta.name)
		return data
	}
	
	fun getData(id: UUID): WorkspaceData? = workspaceList.find { it.id == id }
	
	fun getAll(): List<WorkspaceData> = workspaceList
	
	fun updateMeta(id: UUID, meta: WorkspaceMeta) {
		update(workspaceList.map { if (it.id == id) it.copy(meta = meta) else it })
		logger.debug("Workspace meta updated  id={}", id)
	}
	
	fun updateData(id: UUID, git: Boolean?, sessionIds: List<UUID>?) {
		update(workspaceList.map { if (it.id == id) it.copy(git = git, sessionIds = sessionIds) else it })
		logger.debug("Workspace data updated  id={}", id)
	}
	
	fun delete(id: UUID) {
		if (id == DEFAULT_WORKSPACE_ID) error("Cannot delete default workspace")
		update(workspaceList.filterNot { it.id == id })
		logger.debug("Workspace deleted  id={}", id)
	}
	
	fun getOrCreateDefault(): WorkspaceData {
		return getData(DEFAULT_WORKSPACE_ID) ?: run {
			val defaultPath = Path.of(
				System.getProperty("user.home"), ".config", "autotweaker", "workspace"
			)
			Files.createDirectories(defaultPath)
			val meta = WorkspaceMeta(name = "default", inContainer = false, path = defaultPath)
			val data = WorkspaceData(id = DEFAULT_WORKSPACE_ID, meta = meta)
			update(workspaceList.plus(data))
			logger.info("Default workspace created  id={}  path={}", data.id, defaultPath)
			data
		}
	}
	
	private fun update(new: List<WorkspaceData>) {
		workspaceList = new
		jsonEntry.set(Json.encodeToJsonElement(workspaceList))
	}
}