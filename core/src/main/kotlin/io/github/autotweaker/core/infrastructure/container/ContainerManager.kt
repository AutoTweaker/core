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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.infrastructure.container.docker.DockerJavaService
import io.github.autotweaker.core.infrastructure.persistence.EnvStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

object ContainerManager : Loggable, Traceable, Settable, EnvStore() {
	private val lock = ReentrantMutex()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	
	private var imagePullJob: Deferred<Unit>? = null
	
	private val service: ContainerService = DockerJavaService()
	
	@Volatile
	private var containerId: String? = null
	
	@Volatile
	private var containerAccess = false
	
	val isRunning: Boolean get() = containerId != null
	
	fun init() {
		Files.createDirectories(ContainerConfig().workspaceHostPath)
		
		if (service.checkAccess()) containerAccess = true
		else log.warn("Denied container access, features disabled").also { return }
		
		imagePullJob = scope.async {
			val image = setting.get(ContainerSettings.DockerImage()).value
			service.pullImage(image)
		}
	}
	
	@OptIn(ExperimentalCoroutinesApi::class)
	private suspend fun ensureRunning() = lock.withLock {
		if (isRunning) return@withLock
		val image = setting.get(ContainerSettings.DockerImage()).value
		val job = imagePullJob
		if (job != null && job.isCompleted && job.getCompletionExceptionOrNull() != null)
			imagePullJob = scope.async { service.pullImage(image) }
				.andLog(log) { warn("Failed image pull, retried  image={}", image) }
		
		imagePullJob?.await()
		
		log.debug("Initiated container start  image={}", image)
		containerId = service.start(
			image, ContainerConfig(
				env = listEnv().mapNotNull {
					it to (getEnv(it) ?: return@mapNotNull null)
				}.toMap()
			)
		).andLog(log) { info("Started container  containerId={}", it) }
	}
	
	suspend fun stop() = lock.withLock {
		val id = containerId ?: return@withLock
		trace.catching {
			log.debug("Initiated container stop  containerId={}", id)
			service.stop(id)
		}.also {
			containerId = null
			service.shutdown()
			log.info("Stopped container  containerId={}", id)
		}.getOrThrow()
	}
	
	
	fun execShellStream(
		command: String, workDir: Path?, timeout: Duration, env: Map<String, String>
	): Flow<ShellEvent> = flow {
		if (!containerAccess) {
			val msg = setting.get(ContainerSettings.AccessDeniedMessage()).value
			emit(ShellEvent.Stderr("$msg\n"))
			emit(
				ShellEvent.Exit(
					ShellResult(exitCode = 1, timeout = false, duration = Duration.ZERO)
				)
			)
			return@flow
		}
		ensureRunning()
		val id = containerId ?: throw NoContainerRunningException()
		val wrappedCommand = listOf(
			"timeout", "--signal=KILL", "${timeout.inWholeSeconds}", "bash", "-lc", command
		)
		emitAll(service.execStream(id, wrappedCommand, workDir = workDir, env = env))
	}
}
