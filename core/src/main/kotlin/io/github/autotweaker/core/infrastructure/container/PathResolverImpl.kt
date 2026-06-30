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

package io.github.autotweaker.core.infrastructure.container

import io.github.autotweaker.api.adapter.PathResolver
import java.nio.file.Path

class PathResolverImpl(
	private val containerConfig: ContainerConfig
) : PathResolver {
	override fun inContainer(workspace: Path): Boolean =
		workspace.normalize().startsWith(containerConfig.workspaceHostPath.normalize())
	
	override fun toAbsolutePath(workspace: Path, path: Path): Path {
		val base = if (inContainer(workspace)) toContainerPath(workspace) else workspace
		return base.resolve(path).normalize()
	}
	
	override fun toContainerPath(path: Path): Path =
		containerConfig.workDir.normalize()
			.resolve(containerConfig.workspaceHostPath.normalize().relativize(path.normalize()))
	
	override fun toHostPath(path: Path): Path =
		containerConfig.workspaceHostPath.normalize()
			.resolve(containerConfig.workDir.normalize().relativize(path.normalize()))
}
