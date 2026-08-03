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

package io.github.autotweaker.core.domain.port

import java.nio.file.attribute.PosixFilePermission
import kotlin.time.Instant

data class FileMetadata(
	val size: Long,
	val lastModifiedTime: Instant,
	val lastAccessTime: Instant,
	val creationTime: Instant,
	val isRegularFile: Boolean,
	val isDirectory: Boolean,
	val isSymbolicLink: Boolean,
	val isOther: Boolean,
	val fileKey: String?,
	val owner: String,
	val group: String,
	val permissions: Set<PosixFilePermission>,
)
