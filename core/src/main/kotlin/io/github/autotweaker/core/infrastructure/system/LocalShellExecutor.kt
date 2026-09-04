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

package io.github.autotweaker.core.infrastructure.system

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class LocalShellExecutor : Loggable, Traceable {
	private val drainGrace: Duration = 5.seconds

	fun exec(
		command: String, workDir: Path, env: Map<String, String>, timeout: Duration
	): Flow<ShellEvent> = channelFlow {
		log.debug(
			"Started shell command  command={}  workDir={}  timeout={}", command, workDir, timeout
		)
		val process = withContext(Dispatchers.IO) {
			ProcessBuilder("bash", "-lc", command)
				.directory(workDir.toFile())
				.redirectErrorStream(false)
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
			
			val execDuration = measureTimedValue {
				val (finished, exitCode) = withContext(Dispatchers.IO) {
					val code = withTimeoutOrNull(timeout) {
						suspendCancellableCoroutine { cont ->
							cont.invokeOnCancellation {
								killProcessTree(process)
							}
							process.onExit().whenComplete { p, err ->
								if (err != null) cont.resumeWithException(err)
								else cont.resume(p.exitValue())
							}
						}
					}
					if (code != null) {
						true to code
					} else {
						killProcessTree(process)
						process.waitFor(2, TimeUnit.SECONDS)
						log.warn("Timed out shell command  command={}  timeout={}", command, timeout)
						false to -1
					}
				}
				
				if (withTimeoutOrNull(drainGrace) { stdoutJob.join(); stderrJob.join() } == null) {
					stdoutJob.cancel()
					stderrJob.cancel()
				}

				finished to exitCode
			}
			val (finished, exitCode) = execDuration.value
			log.debug(
				"Completed shell command  command={}  exitCode={}  duration={}",
				command,
				exitCode,
				execDuration.duration
			)
			send(
				ShellEvent.Exit(
					ShellResult(
						exitCode = exitCode,
						timeout = !finished,
						duration = execDuration.duration,
					)
				)
			)
		} finally {
			killProcessTree(process)
		}
	}

	private fun killProcessTree(process: Process) {
		process.descendants().forEach { it.destroyForcibly() }
		process.destroyForcibly()
	}
}
