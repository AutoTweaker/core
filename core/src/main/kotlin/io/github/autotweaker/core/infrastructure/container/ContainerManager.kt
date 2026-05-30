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
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.container.docker.DockerJavaService
import io.github.autotweaker.core.infrastructure.persistence.EnvStorage
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Duration

object ContainerManager {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val mutex = Mutex()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private lateinit var envStorage: EnvStorage
	private var imagePullJob: Deferred<Unit>? = null
	
	private val service: ContainerService = DockerJavaService()
	
	@Volatile
	private var _containerId: String? = null
	
	val isRunning: Boolean get() = _containerId != null
	
	@Suppress("unused")
	val containerId: String? get() = _containerId
	
	@Synchronized
	fun init(secretStore: SecretStore) {
		envStorage = EnvStorage(this::class, secretStore)
		imagePullJob = scope.async {
			val image = Settings.get(ContainerSettings.DockerImage()).value
			service.pullImage(image)
		}
	}
	
	private suspend fun ensureRunning() = mutex.withLock {
		if (_containerId != null) return@withLock
		imagePullJob?.await()
		val containerConfig =
			ContainerConfig(name = Settings.get(ContainerSettings.ContainerName()).value, env = getEnv())
		val image = Settings.get(ContainerSettings.DockerImage()).value
		logger.debug("Container start initiated  image={}", image)
		val id = service.start(image, containerConfig)
		_containerId = id
		logger.info("Container started  containerId={}", id)
	}
	
	suspend fun stop() {
		mutex.withLock {
			val id = _containerId ?: return@withLock
			val svc = service
			try {
				logger.debug("Container stop initiated  containerId={}", id)
				svc.stop(id)
			} finally {
				_containerId = null
				service.shutdown()
				logger.info("Container stopped  containerId={}", id)
			}
		}
	}
	
	fun execShellStream(
		command: String, workDir: Path?, timeout: Duration, env: Map<String, String>
	): Flow<ShellEvent> = flow {
		ensureRunning()
		val id = _containerId ?: throw NoContainerRunningException()
		emitAll(service.execStream(id, listOf("bash", "-lc", command), workDir = workDir, timeout = timeout, env = env))
	}
	
	fun listEnv(): List<String> = envStorage.listEnv()
	
	@Synchronized
	fun setEnv(id: String, value: String) {
		envStorage.setEnv(id, value)
		logger.debug("Container env set  key={}", id)
	}
	
	@Synchronized
	fun removeEnv(id: String) {
		envStorage.removeEnv(id)
		logger.debug("Container env removed  key={}", id)
	}
	
	fun getEnv(id: String? = null): Map<String, String> {
		val ids = if (id != null) {
			if (id in envStorage.listEnv()) listOf(id) else emptyList()
		} else {
			envStorage.listEnv()
		}
		return ids.mapNotNull { key ->
			val value = envStorage.getEnv(key)
			if (value == null) {
				logger.warn("Failed to get env value  key={}", key)
				null
			} else {
				key to value
			}
		}.toMap()
	}
}
