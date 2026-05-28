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
import java.util.concurrent.atomic.AtomicReference

object WorkspaceManager {
	val DEFAULT_WORKSPACE_ID: UUID = UUID.nameUUIDFromBytes("autotweaker-default-workspace".toByteArray())
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStoreImpl.namespace(this::class)
	
	private val workspaceListRef = AtomicReference<List<WorkspaceData>>(emptyList())
	
	init {
		val jsonArray = jsonEntry.get()
		workspaceListRef.set(
			if (jsonArray == null) emptyList()
			else Json.decodeFromJsonElement<List<WorkspaceData>>(jsonArray)
		)
		logger.info("WorkspaceManager initialized  count={}", workspaceListRef.get().size)
	}
	
	fun create(meta: WorkspaceMeta): WorkspaceData {
		if (workspaceListRef.get().any { it.meta.id == meta.id }) error("Workspace ${meta.id} already exists")
		val data = WorkspaceData(meta = meta)
		update(workspaceListRef.get().plus(data))
		logger.debug("Workspace created  id={}  name={}", data.meta.id, meta.displayName)
		return data
	}
	
	fun getData(id: UUID): WorkspaceData? = workspaceListRef.get().find { it.meta.id == id }
	
	fun getAll(): List<WorkspaceData> = workspaceListRef.get()
	
	fun updateMeta(meta: WorkspaceMeta) {
		require(meta.id in workspaceListRef.get().map { it.meta.id })
		update(workspaceListRef.get().map { if (it.meta.id == meta.id) it.copy(meta = meta) else it })
		logger.debug("Workspace meta updated  id={}", meta.id)
	}
	
	fun updateData(id: UUID, sessionIds: List<UUID>?) {
		update(workspaceListRef.get().map { if (it.meta.id == id) it.copy(sessionIds = sessionIds) else it })
		logger.debug("Workspace data updated  id={}", id)
	}
	
	fun delete(id: UUID) {
		require(id in workspaceListRef.get().map { it.meta.id })
		if (id == DEFAULT_WORKSPACE_ID) error("Cannot delete default workspace")
		update(workspaceListRef.get().filterNot { it.meta.id == id })
		logger.debug("Workspace deleted  id={}", id)
	}
	
	fun getOrCreateDefault(): WorkspaceData {
		return getData(DEFAULT_WORKSPACE_ID) ?: run {
			val defaultPath = Path.of(
				System.getProperty("user.home"), ".config", "autotweaker", "workspace"
			)
			Files.createDirectories(defaultPath)
			val meta = WorkspaceMeta(
				id = DEFAULT_WORKSPACE_ID, displayName = "default", inContainer = false, path = defaultPath
			)
			val data = WorkspaceData(meta = meta)
			update(workspaceListRef.get().plus(data))
			logger.info("Default workspace created  id={}  path={}", data.meta.id, defaultPath)
			data
		}
	}
	
	private fun update(new: List<WorkspaceData>) {
		workspaceListRef.set(new)
		jsonEntry.set(Json.encodeToJsonElement(workspaceListRef.get()))
	}
}
