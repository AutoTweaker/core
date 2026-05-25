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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.infrastructure.persistence.WorkspaceManager
import java.nio.file.Files
import java.util.*

object WorkspaceAPI {
	private val wsm = WorkspaceManager
	
	fun create(meta: WorkspaceMeta): WorkspaceData {
		if (!Files.isDirectory(meta.path)) error("${meta.path} is not a directory")
		val data = wsm.create(meta)
		if (Files.isDirectory(meta.path.resolve(".git"))) {
			wsm.updateData(data.id, git = true, null)
		}
		return data
	}
	
	suspend fun rename(id: UUID, newName: String) {
		val data = wsm.getData(id) ?: error("Workspace not found: $id")
		wsm.updateMeta(id, meta = data.meta.copy(name = newName))
		SessionManager.updateWorkspaceName(id, newName)
	}
	
	suspend fun delete(id: UUID) {
		val data = wsm.getData(id) ?: error("Workspace not found: $id")
		data.sessionIds?.forEach { SessionManager.delete(it) }
		wsm.delete(id)
	}
	
	fun list() = wsm.getAll()
}