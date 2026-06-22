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
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.container.docker.DockerJavaService
import io.github.autotweaker.core.infrastructure.persistence.EnvStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

object ContainerManager : Loggable, JsonStorable, Settable {
	private val mutex = Mutex()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private lateinit var envStorage: EnvStorage
	private var imagePullJob: Deferred<Unit>? = null
	
	private val service: ContainerService = DockerJavaService()
	
	@Volatile
	private var _containerId: String? = null
	
	@Volatile
	private var containerAccess = true
	
	val isRunning: Boolean get() = _containerId != null
	
	@Suppress("unused")
	val containerId: String? get() = _containerId
	
	@Synchronized
	fun init(secretStore: SecretStore) {
		envStorage = EnvStorage(this::class, store, secretStore)
		Files.createDirectories(ContainerConfig().workspaceHostPath)
		if (!service.checkAccess()) {
			containerAccess = false
			log.warn("Denied container access  features disabled")
			return
		}
		imagePullJob = scope.async {
			val image = setting.get(ContainerSettings.DockerImage()).value
			service.pullImage(image)
		}
	}
	
	@OptIn(ExperimentalCoroutinesApi::class)
	private suspend fun ensureRunning() = mutex.withLock {
		if (_containerId != null) return@withLock
		val image = setting.get(ContainerSettings.DockerImage()).value
		val job = imagePullJob
		if (job != null && job.isCompleted && job.getCompletionExceptionOrNull() != null) {
			log.warn("Failed image pull, retried  image={}", image)
			imagePullJob = scope.async { service.pullImage(image) }
		}
		imagePullJob?.await()
		val containerConfig =
			ContainerConfig(name = setting.get(ContainerSettings.ContainerName()).value, env = getEnv())
		log.debug("Initiated container start  image={}", image)
		_containerId = service.start(image, containerConfig)
			.andLog(log) { info("Started container  containerId={}", it) }
	}
	
	suspend fun stop() {
		mutex.withLock {
			val id = _containerId ?: return@withLock
			try {
				log.debug("Initiated container stop  containerId={}", id)
				service.stop(id)
			} finally {
				_containerId = null
				service.shutdown()
				log.info("Stopped container  containerId={}", id)
			}
		}
	}
	
	fun execShellStream(
		command: String, workDir: Path?, timeout: Duration, env: Map<String, String>
	): Flow<ShellEvent> = flow {
		if (!containerAccess) {
			val msg = setting.get(ContainerSettings.AccessDeniedMessage()).value
			emit(ShellEvent.Stderr("$msg\n"))
			emit(ShellEvent.Exit(ShellResult(exitCode = 1, timeout = false, duration = Duration.ZERO)))
			return@flow
		}
		ensureRunning()
		val id = _containerId ?: throw NoContainerRunningException()
		val wrappedCommand = listOf("timeout", "--signal=KILL", "${timeout.inWholeSeconds}", "bash", "-lc", command)
		emitAll(service.execStream(id, wrappedCommand, workDir = workDir, env = env))
	}
	
	suspend fun listEnv(): List<String> = envStorage.listEnv()
	
	suspend fun setEnv(id: String, value: String) {
		envStorage.setEnv(id, value)
		log.debug("Set container env  key={}", id)
	}
	
	suspend fun removeEnv(id: String) {
		envStorage.removeEnv(id)
		log.debug("Removed container env  key={}", id)
	}
	
	suspend fun getEnv(id: String? = null): Map<String, String> {
		val ids = if (id != null) {
			if (id in envStorage.listEnv()) listOf(id) else emptyList()
		} else {
			envStorage.listEnv()
		}
		return ids.mapNotNull { key ->
			val value = envStorage.getEnv(key)
			if (value == null) {
				log.warn("Failed env value retrieval  key={}", key)
				null
			} else {
				key to value
			}
		}.toMap()
	}
}
