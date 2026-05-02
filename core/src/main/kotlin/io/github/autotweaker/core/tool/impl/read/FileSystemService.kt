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

package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.Unicode
import java.nio.file.Path

interface FileSystemService {
	fun normalize(filePath: String): Path
	suspend fun exists(path: Path): Boolean
	suspend fun isRegularFile(path: Path): Boolean
	suspend fun readUnicode(path: Path): List<Unicode>
	suspend fun readAllLines(path: Path): List<String>
	suspend fun sha256(path: Path): String
}