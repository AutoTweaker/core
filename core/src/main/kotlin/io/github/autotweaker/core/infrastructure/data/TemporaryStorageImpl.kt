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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.TMP_PATH
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.trace
import io.github.autotweaker.core.domain.port.TemporaryStorage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.streams.asSequence

object TemporaryStorageImpl : TemporaryStorage, Loggable, Traceable {
	private val dirExists get() = Files.exists(TMP_PATH)
	
	override fun save(content: String): Pair<UUID, Path> {
		require(content.isNotEmpty())
		createDir()
		
		val id = UUID.randomUUID()
		val path = TMP_PATH.resolve(id.toString())
		path.writeText(content)
		
		return id to path
	}
	
	override fun list(): Map<UUID, Path> {
		if (!dirExists) return emptyMap()
		
		val files = trace.catching {
			Files.list(TMP_PATH).use { stream ->
				stream.asSequence().toList()
			}
		}.rethrowNot<IOException>()
			.getOrElse { return emptyMap() }
		val map = mutableMapOf<UUID, Path>()
		files.forEach {
			val id = trace.catching {
				UUID.fromString(it.fileName.toString())
			}.getOrElse { return@forEach }
			map[id] = it
		}
		return map.toMap()
	}
	
	override fun read(id: UUID): String? {
		val path = TMP_PATH.resolve(id.toString())
		if (!dirExists || Files.notExists(path)) return null
		
		return trace.catching {
			path.readText()
		}.rethrowNot<IOException>()
			.getOrNull()
	}
	
	private fun createDir() {
		if (!dirExists) Files.createDirectories(TMP_PATH)
	}
}
