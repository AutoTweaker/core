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

import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ContainerShellExecutor {
	fun exec(command: String, workDir: Path, env: Map<String, String>, timeout: Duration): Flow<ShellEvent> = flow {
		val startNs = System.nanoTime()
		ContainerManager.execShellStream(command, workDir, timeout, env).collect { event ->
			when (event) {
				is ShellEvent.Exit -> {
					val duration = ((System.nanoTime() - startNs) / 1_000_000_000.0).seconds
					emit(ShellEvent.Exit(ShellResult(event.result.exitCode, event.result.timeout, duration)))
				}
				
				else -> emit(event)
			}
		}
	}
}
