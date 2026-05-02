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

package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.container.ContainerManager
import io.github.autotweaker.core.tool.impl.bash.BashService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BashServiceImpl(
	private val workspacePath: Path,
	private val inContainer: Boolean,
	private val containerWorkDir: Path,
) : BashService {
	override suspend fun run(command: String, timeoutSeconds: Int, env: Map<String, String>): BashService.Result =
		if (inContainer) runInContainer(command, timeoutSeconds, env) else withContext(Dispatchers.IO) {
			val startNs = System.nanoTime()
			val process = ProcessBuilder("bash", "-lc", command)
				.directory(workspacePath.toFile())
				.redirectErrorStream(false)
				.apply { environment().putAll(env) }
				.start()
			
			val pool = Executors.newFixedThreadPool(2)
			try {
				val stdoutFuture = pool.submit(Callable { process.inputStream.bufferedReader().readText() })
				val stderrFuture = pool.submit(Callable { process.errorStream.bufferedReader().readText() })
				val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
				if (!finished) {
					process.destroyForcibly()
					process.waitFor(2, TimeUnit.SECONDS)
				}
				val stdout = stdoutFuture.get(2, TimeUnit.SECONDS)
				val stderr = stderrFuture.get(2, TimeUnit.SECONDS)
				val durationSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0
				BashService.Result(
					exitCode = if (finished) process.exitValue() else -1,
					stdout = stdout,
					stderr = stderr,
					timeout = !finished,
					durationSeconds = durationSeconds,
				)
			} finally {
				pool.shutdownNow()
			}
		}
	
	private suspend fun runInContainer(
		command: String,
		timeoutSeconds: Int,
		env: Map<String, String>
	): BashService.Result {
		val startNs = System.nanoTime()
		val envPrefix = env.entries.joinToString(" ") { (k, v) -> "${k.shellEscape()}=${v.shellEscape()}" }
		val wrapped = buildString {
			append("cd ${containerWorkDir.toString().shellEscape()} && ")
			if (envPrefix.isNotBlank()) append("env $envPrefix ")
			append("timeout ${timeoutSeconds}s bash -lc ${command.shellEscape()}")
		}
		val result = ContainerManager.exec("bash", "-lc", wrapped)
		val timeout = result.exitCode == 124
		val durationSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0
		return BashService.Result(result.exitCode, result.stdout, result.stderr, timeout, durationSeconds)
	}
	
	private fun String.shellEscape(): String = "'${replace("'", "'\"'\"'")}'"
}
