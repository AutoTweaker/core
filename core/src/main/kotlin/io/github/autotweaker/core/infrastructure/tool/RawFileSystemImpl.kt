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

package io.github.autotweaker.core.infrastructure.tool

import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.core.domain.port.RawFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object RawFileSystemImpl : RawFileSystem {
	override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) { Files.exists(path) }
	
	override suspend fun isRegularFile(path: Path): Boolean = withContext(Dispatchers.IO) { Files.isRegularFile(path) }
	
	override suspend fun readString(path: Path): String = withContext(Dispatchers.IO) { Files.readString(path) }
	
	override suspend fun readAllLines(path: Path): List<String> =
		withContext(Dispatchers.IO) { Files.readAllLines(path) }
	
	override suspend fun readUnicode(path: Path): List<Unicode> = withContext(Dispatchers.IO) {
		Files.readString(path).map { Unicode.fromChar(it) }
	}
	
	override suspend fun sha256(path: Path): String = withContext(Dispatchers.IO) {
		val digest = MessageDigest.getInstance("SHA-256")
		Files.newInputStream(path).use { input ->
			val buffer = ByteArray(8192)
			var bytesRead: Int
			while (input.read(buffer).also { bytesRead = it } != -1) {
				digest.update(buffer, 0, bytesRead)
			}
		}
		digest.digest().joinToString("") { "%02x".format(it) }
	}
}
