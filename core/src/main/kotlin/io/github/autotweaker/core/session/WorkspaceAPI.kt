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

package io.github.autotweaker.core.session

import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.session.workspace.WorkspaceManager
import java.nio.file.Files

object WorkspaceAPI {
	private val wsm = WorkspaceManager
	
	fun create(meta: WorkspaceMeta) {
		if (!Files.isDirectory(meta.path)) error("${meta.path} is not a directory")
		wsm.create(meta)
		if (Files.isDirectory(meta.path.resolve(".git"))) {
			wsm.updateData(meta.name, git = true, null)
		}
	}
	
	suspend fun updateName(name: String, newName: String) {
		val oldMeta = getWorkspace(name).meta
		wsm.updateMeta(name = name, meta = oldMeta.copy(name = newName))
		SessionManager.updateWorkspaceName(name, newName)
	}
	
	suspend fun delete(name: String) {
		getWorkspace(name).sessionIds?.forEach { SessionManager.delete(it) }
		wsm.delete(name)
	}
	
	fun list() = wsm.getAll()
	
	private fun getWorkspace(name: String) = wsm.getData(name) ?: error("Workspace not found: $name")
}