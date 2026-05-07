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
import java.util.*


@Suppress("unused")
object WorkspaceManager {
	private val jsonEntry = JsonStore.namespace(this::class.java.name)
	
	private var workspaceList: List<WorkspaceData>
	
	init {
		val jsonArray = jsonEntry.get()
		
		workspaceList = if (jsonArray == null) emptyList()
		else Json.decodeFromJsonElement<List<WorkspaceData>>(jsonArray)
	}
	
	fun create(meta: Workspace) {
		update(workspaceList.plus(WorkspaceData(meta)))
	}
	
	fun get(name: String): WorkspaceData? =
		workspaceList.find { it.meta.name == name }
	
	fun getAll(): List<Workspace> = workspaceList.map { it.meta }
	
	fun updateMeta(meta: Workspace) {
		val newList = workspaceList.map { if (it.meta.name == meta.name) it.copy(meta = meta) else it }
		update(newList)
	}
	
	fun updateData(name: String, git: Boolean?, sessionIds: List<UUID>?) {
		val newList =
			workspaceList.map { if (it.meta.name == name) it.copy(git = git, sessionIds = sessionIds) else it }
		update(newList)
	}
	
	fun delete(name: String) {
		val newList = workspaceList.filterNot { it.meta.name == name }
		update(newList)
	}
	
	private fun update(new: List<WorkspaceData>) {
		workspaceList = new
		jsonEntry.set(Json.encodeToJsonElement(workspaceList))
	}
}