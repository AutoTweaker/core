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

import io.github.autotweaker.api.TMP_HOST_PATH
import io.github.autotweaker.api.WORKSPACE_HOST_PATH
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import java.nio.file.Path

object PathResolverImpl : PathResolver {
	override fun inContainer(workspace: Path): Boolean =
		workspace.normalize().startsWith(WORKSPACE_HOST_PATH)
	
	override fun toAbsolutePath(workspace: Path, path: Path): Path {
		val base = if (inContainer(workspace)) toContainerPath(workspace) else workspace
		return base.resolve(path).normalize()
	}
	
	override fun toRelativePath(workspace: Path, path: Path): Path {
		val base = if (inContainer(workspace)) toContainerPath(workspace) else workspace
		return base.relativize(toAbsolutePath(workspace, path))
	}
	
	override fun toContainerPath(path: Path): Path {
		val normalized = path.normalize()
		if (normalized.startsWith(WORKSPACE_HOST_PATH))
			return CONTAINER_WORK_PATH.resolve(WORKSPACE_HOST_PATH.relativize(normalized))
		if (normalized.startsWith(TMP_HOST_PATH))
			return CONTAINER_TMP_PATH.resolve(TMP_HOST_PATH.relativize(normalized))
		throw PathOutsideWorkspaceException(path)
	}
	
	override fun toHostPath(path: Path): Path {
		val normalized = path.normalize()
		if (normalized.startsWith(CONTAINER_WORK_PATH))
			return WORKSPACE_HOST_PATH.resolve(CONTAINER_WORK_PATH.relativize(normalized))
		if (normalized.startsWith(CONTAINER_TMP_PATH))
			return TMP_HOST_PATH.resolve(CONTAINER_TMP_PATH.relativize(normalized))
		throw PathOutsideWorkspaceException(path)
	}
}
