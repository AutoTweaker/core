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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log

class LocalShellExecutor : Loggable {
	fun exec(command: String, workDir: Path, env: Map<String, String>, timeout: Duration): Flow<ShellEvent> =
		channelFlow {
			val startNs = System.nanoTime()
			log.debug(
				"Started shell command  command={}  workDir={}  timeout={}s", command, workDir, timeout.inWholeSeconds
			)
			val process = withContext(Dispatchers.IO) {
				ProcessBuilder("bash", "-lc", command).directory(workDir.toFile()).redirectErrorStream(false)
					.apply { environment().putAll(env) }.start()
			}
			try {
				val stdoutJob = launch(Dispatchers.IO) {
					process.inputStream.bufferedReader().use { reader ->
						var line = reader.readLine()
						while (line != null) {
							send(ShellEvent.Stdout(line))
							line = reader.readLine()
						}
					}
				}
				
				val stderrJob = launch(Dispatchers.IO) {
					process.errorStream.bufferedReader().use { reader ->
						var line = reader.readLine()
						while (line != null) {
							send(ShellEvent.Stderr(line))
							line = reader.readLine()
						}
					}
				}
				
				val finished = withContext(Dispatchers.IO) {
					process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
				}
				if (!finished) {
					process.destroyForcibly()
					withContext(Dispatchers.IO) { process.waitFor(2, TimeUnit.SECONDS) }
					log.warn("Timed out shell command  command={}  timeout={}s", command, timeout.inWholeSeconds)
				}
				
				stdoutJob.join()
				stderrJob.join()
				
				val duration = ((System.nanoTime() - startNs) / 1_000_000_000.0).seconds
				val exitCode = if (finished) process.exitValue() else -1
				log.debug(
					"Completed shell command  command={}  exitCode={}  duration={}s",
					command,
					exitCode,
					duration.inWholeSeconds
				)
				send(
					ShellEvent.Exit(
						ShellResult(
							exitCode = exitCode,
							timeout = !finished,
							duration = duration,
						)
					)
				)
			} finally {
				process.destroyForcibly()
			}
		}
}