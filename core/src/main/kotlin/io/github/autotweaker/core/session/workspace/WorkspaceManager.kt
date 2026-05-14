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

package io.github.autotweaker.core.session.workspace

import io.github.autotweaker.core.data.json.JsonStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.*

object WorkspaceManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val jsonEntry = JsonStore.namespace(this::class.java.name)
	
	private var workspaceList: List<WorkspaceData>
	
	init {
		val jsonArray = jsonEntry.get()
		workspaceList = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement<List<WorkspaceData>>(jsonArray)
		logger.info("WorkspaceManager initialized  count={}", workspaceList.size)
	}
	
	fun create(meta: WorkspaceMeta) {
		update(workspaceList.plus(WorkspaceData(meta)))
		logger.debug("Workspace created  name={}", meta.name)
	}
	
	fun getData(name: String): WorkspaceData? = workspaceList.find { it.meta.name == name }
	
	fun getAll(): List<WorkspaceMeta> = workspaceList.map { it.meta }
	
	fun updateMeta(name: String, meta: WorkspaceMeta) {
		update(workspaceList.map { if (it.meta.name == name) it.copy(meta = meta) else it })
		logger.debug("Workspace meta updated  name={}", name)
	}
	
	fun updateData(name: String, git: Boolean?, sessionIds: List<UUID>?) {
		update(workspaceList.map { if (it.meta.name == name) it.copy(git = git, sessionIds = sessionIds) else it })
		logger.debug("Workspace data updated  name={}", name)
	}
	
	fun delete(name: String) {
		update(workspaceList.filterNot { it.meta.name == name })
		logger.debug("Workspace deleted  name={}", name)
	}
	
	private fun update(new: List<WorkspaceData>) {
		workspaceList = new
		jsonEntry.set(Json.encodeToJsonElement(workspaceList))
	}
}
