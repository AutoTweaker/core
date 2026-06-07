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

import io.github.autotweaker.api.types.shell.ShellEvent
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface ContainerService {
	suspend fun pullImage(image: String)
	suspend fun start(image: String, config: ContainerConfig): String
	suspend fun stop(containerId: String)
	fun shutdown() {}
	fun execStream(
		containerId: String,
		command: List<String>,
		workDir: Path? = null,
		env: Map<String, String> = emptyMap(),
	): Flow<ShellEvent>
}
