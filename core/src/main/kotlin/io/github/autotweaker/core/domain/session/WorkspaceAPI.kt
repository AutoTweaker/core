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
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object WorkspaceAPI {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val wsm = WorkspaceManager
	
	fun create(meta: WorkspaceMeta): WorkspaceData {
		require(!wsm.getAll().any { it.meta.displayName == meta.displayName })
		val home = Path.of(System.getProperty("user.home"))
		val resolved = if (meta.path.isAbsolute) meta.path else home.resolve(meta.path)
		val meta = meta.copy(path = resolved)
		if (!Files.isDirectory(meta.path)) error("${meta.path} is not a directory")
		val data = wsm.create(meta)
		logger.info("Created workspace  id={} name={} path={}", data.meta.id, meta.displayName, meta.path)
		return data
	}
	
	fun rename(id: UUID, newName: String) {
		val data = wsm.getData(id) ?: error("Workspace not found: $id")
		require(!wsm.getAll().any { it.meta.displayName == newName })
		wsm.updateMeta(data.meta.copy(displayName = newName))
		logger.info("Renamed workspace  id={} newName={}", id, newName)
	}
	
	suspend fun delete(id: UUID) {
		val data = wsm.getData(id) ?: error("Workspace not found: $id")
		data.sessionIds?.forEach { SessionManager.delete(it) }
		wsm.delete(id)
		logger.info("Deleted workspace  id={} sessionCount={}", id, data.sessionIds?.size ?: 0)
	}
	
	fun list() = wsm.getAll()
}
