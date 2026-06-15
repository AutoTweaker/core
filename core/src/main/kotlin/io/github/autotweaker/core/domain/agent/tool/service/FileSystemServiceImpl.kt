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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.api.types.Unicode
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import java.nio.file.Path

class FileSystemServiceImpl(
	private val fs: RawFileSystem,
	private val containerConfig: ContainerConfig,
	private val workspace: WorkspaceMeta,
) : FileSystemService {
	private val inContainer: Boolean get() = containerConfig.isContainerPath(workspace.path)
	private val containerMount: Path get() = containerConfig.workDir.normalize()
	private val hostMount: Path get() = containerConfig.workspaceHostPath.normalize()
	private val workspaceInContainer: Path get() = containerMount.resolve(hostMount.relativize(workspace.path))
	
	override fun normalize(filePath: String): Path {
		val path = Path.of(filePath)
		return if (path.isAbsolute) path.normalize() else workspaceInContainer.resolve(path).normalize()
	}
	
	private fun resolve(path: Path): Path {
		if (!inContainer) return path
		if (!path.startsWith(containerMount)) throw FileSystemService.PathOutsideWorkspaceException(path)
		return hostMount.resolve(containerMount.relativize(path))
	}
	
	override suspend fun exists(path: Path): Boolean = fs.exists(resolve(path))
	
	override suspend fun isRegularFile(path: Path): Boolean = fs.isRegularFile(resolve(path))
	
	override suspend fun readUnicode(path: Path): List<Unicode> = fs.readUnicode(resolve(path))
	
	override suspend fun readAllLines(path: Path): List<String> = fs.readAllLines(resolve(path))
	
	override suspend fun sha256(path: Path): String = fs.sha256(resolve(path))
}
