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

package io.github.autotweaker.core.infrastructure.data

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.core.domain.port.TemporaryStorage
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

object TemporaryStorageImpl : TemporaryStorage, Loggable, Traceable {
	override fun save(content: String, container: Boolean): Pair<UUID, Path> {
		if (!TMP_HOST_PATH.exists()) TMP_HOST_PATH.createDirectories()
		
		val id = UUID()
		val base = baseDir(container)
		val path = base / id.toString()
		path.writeText(content)
		
		return id to path
	}
	
	override fun list(container: Boolean): Map<UUID, Path> {
		val base = baseDir(container)
		if (!base.exists()) return emptyMap()
		
		val files = trace.catching {
			base.listDirectoryEntries()
		}.getOrElse { return emptyMap() }
		
		return buildMap {
			files.forEach {
				val id = trace.catching {
					UUID.fromString(it.name)
				}.getOrElse { return@forEach }
				
				this[id] = it
			}
		}
	}
	
	override fun read(id: UUID, container: Boolean): String? {
		val base = baseDir(container)
		val path = base / id.toString()
		if (base.notExists() || path.notExists()) return null
		
		return trace.catching {
			path.readText()
		}.getOrNull()
	}
	
	private fun baseDir(container: Boolean) = if (container) TMP_HOST_PATH else TMP_PATH
}
