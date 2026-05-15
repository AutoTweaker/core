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

package io.github.autotweaker.core.container

import java.nio.file.Path

data class ContainerConfig(
	val name: String = "autotweaker-workspace",
	val env: Map<String, String> = emptyMap(),
	val workDir: Path = Path.of("/workspace"),
	val workspaceHostPath: Path = Path.of(
		System.getProperty("user.home"),
		".config",
		"autotweaker",
		"container",
		"workspace"
	),
)
