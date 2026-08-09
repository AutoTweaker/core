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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.exception.*
import io.github.autotweaker.api.types.exception.notfound.*
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object WorkspaceAPI : Loggable {
	private val wsm = WorkspaceManager
	
	suspend fun create(meta: WorkspaceMeta): WorkspaceData {
		val home = Path.of(System.getProperty("user.home"))
		val resolved = if (meta.path.isAbsolute) meta.path else home.resolve(meta.path)
		val meta = meta.copy(path = resolved)
		
		if (!Files.isDirectory(meta.path)) throw InvalidWorkspacePathException(meta.path)
		
		return wsm.create(meta).andLog(log) {
			info("Created workspace  id={}  name={}  path={}", it.meta.id, it.meta.displayName, it.meta.path)
		}
	}
	
	suspend fun rename(id: UUID, newName: String) =
		wsm.updateMeta {
			val data = wsm.getData(id) ?: throw WorkspaceNotFoundException(id)
			data.meta.copy(displayName = newName)
		}.andLog(log) {
			info("Renamed workspace  id={}  newName={}", id, newName)
		}
	
	suspend fun delete(id: UUID): Boolean = wsm.delete(id)
	
	suspend fun list() = wsm.getAll()
}
