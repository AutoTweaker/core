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

package io.github.autotweaker.core.domain.tool.port

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.domain.port.FileContent
import java.nio.file.Path

interface FileSystemService {
	fun normalize(filePath: String): Path
	fun displayPath(path: Path): Path // 解析相对路径
	suspend fun exists(path: Path): Boolean
	suspend fun isRegularFile(path: Path): Boolean
	suspend fun read(path: Path): FileContent
	suspend fun sha256(path: Path): Sha256
	suspend fun create(path: Path, content: String): Sha256
	suspend fun update(path: Path, expected: Sha256, new: String): Sha256
	suspend fun glob(pattern: String, cwd: Path): List<Path>
}
