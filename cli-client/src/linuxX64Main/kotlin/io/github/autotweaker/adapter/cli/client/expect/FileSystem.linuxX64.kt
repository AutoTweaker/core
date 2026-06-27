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

package io.github.autotweaker.adapter.cli.client.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.io.files.Path
import platform.posix.*

actual fun createSymbolicLink(link: Path, source: Path) {
	val linkStr = link.toString()
	val sourceStr = source.toString()
	
	unlink(linkStr)
	
	if (symlink(sourceStr, linkStr) == -1)
		perror("symlink")
}

@OptIn(ExperimentalForeignApi::class)
actual fun Path.isSocket(): Boolean {
	val pathStr = toString()
	
	return memScoped {
		val statBuf = alloc<stat>()
		if (stat(pathStr, statBuf.ptr) == 0)
			(statBuf.st_mode.toInt() and S_IFMT) == S_IFSOCK
		else false
	}
}
