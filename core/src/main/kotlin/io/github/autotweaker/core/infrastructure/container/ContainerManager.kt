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
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

class ContainerManager(
	private val secret: SecretStore,
	private val service: ContainerService
) : Loggable, Traceable, EnvStore() {
	private val lock = ReentrantMutex()
	private val scope = scope(IO)
	
	private val image = ContainerSettings.DockerImage().get()
	private var imagePullJob: Deferred<Unit>? = null
	
	@Volatile
	private var containerId: String? = null
	
	@Volatile
	private var containerAccess = false
	
	val isRunning: Boolean get() = containerId != null
	
	fun init() {
		Files.createDirectories(WORKSPACE_HOST_PATH)
		
		if (service.checkAccess()) containerAccess = true
		else log.warn("Denied container access, features disabled").also { return }
		
		imagePullJob = scope.async {
			service.pull(image)
		}
	}
	
	private suspend fun ensureRunning() = lock.withLock {
		if (isRunning) return@withLock
		secret.requireUnlocked()
		trace.catching { imagePullJob?.await() }
			.ensureActive()
			.onFailure {
				log.warn("Failed image pull, retried  image={}", image)
				imagePullJob = scope.async { service.pull(image) }
			}
		
		imagePullJob?.await()
		
		log.debug("Initiated container start  image={}", image)
		containerId = service.start(image).andLog(log) {
			info("Started container  containerId={}", it)
		}
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
	
	
	fun exec(
		command: String, workDir: Path, env: Map<String, String>, timeout: Duration
	): Flow<ShellEvent> = flow {
		if (!containerAccess) {
			val msg = ContainerSettings.AccessDeniedMessage().get()
			emit(ShellEvent.Stderr("$msg\n"))
			emit(
				ShellEvent.Exit(
					ShellResult(exitCode = -1, timeout = false, duration = Duration.ZERO)
				)
			)
			return@flow
		}
		ensureRunning()
		val id = containerId ?: error("Failed to start container")
		emitAll(
			service.exec(
				id, listOf("bash", "-lc", command), workDir,
				env = listEnv().mapNotNull {
					it to (getEnv(it) ?: return@mapNotNull null)
				}.toMap() + env,
				timeout = timeout
			)
		)
	}
}
