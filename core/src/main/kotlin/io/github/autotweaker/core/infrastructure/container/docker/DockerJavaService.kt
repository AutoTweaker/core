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

package io.github.autotweaker.core.infrastructure.container.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.sun.security.auth.module.UnixSystem
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.container.ContainerOperationException
import io.github.autotweaker.core.infrastructure.container.ContainerService
import io.github.autotweaker.core.infrastructure.persistence.config.Settings
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.time.Duration as JavaDuration

class DockerJavaService : ContainerService {
	
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	
	private val uidGid: String = run {
		val unix = UnixSystem()
		"${unix.uid}:${unix.gid}"
	}
	
	private var workspaceHostPath: Path? = null
	private var containerWorkDir: Path? = null
	private var permissionFixJob: Job? = null
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	
	private val client: DockerClient = run {
		val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
		val httpClient = ApacheDockerHttpClient.Builder()
			.dockerHost(config.dockerHost)
			.sslConfig(config.sslConfig)
			.maxConnections(100)
			.connectionTimeout(JavaDuration.ofSeconds(30))
			.responseTimeout(JavaDuration.ofSeconds(45))
			.build()
		DockerClientImpl.getInstance(config, httpClient)
	}
	
	override fun shutdown() {
		scope.cancel()
		trace.catching { client.close() }
	}
	
	override fun checkAccess(): Boolean = trace.catching {
		client.pingCmd().exec()
		true
	}.getOrDefault(false)
	
	override suspend fun pullImage(image: String) = withContext(Dispatchers.IO) {
		client.pullImageCmd(image).exec(object : PullImageResultCallback() {}).awaitCompletion()
		logger.info("Pulled image  image={}", image)
	}
	
	override suspend fun start(image: String, config: ContainerConfig): String = withContext(Dispatchers.IO) {
		try {
			val hostPath = config.workspaceHostPath
			this@DockerJavaService.workspaceHostPath = hostPath
			this@DockerJavaService.containerWorkDir = config.workDir
			Files.createDirectories(hostPath)
			
			val existing = findContainerByName(config.name)
			if (existing != null) {
				if (existing.state != "running") {
					client.startContainerCmd(existing.id).exec()
					logger.info("Restarted container  containerId={}", existing.id)
				} else {
					logger.debug("Found container already running  containerId={}", existing.id)
				}
				return@withContext existing.id
			}
			
			val hostConfig = HostConfig().withBinds(
				Bind(
					hostPath.toString(), Volume(config.workDir.toString())
				)
			).withExtraHosts("host.docker.internal:host-gateway").withInit(true)
			
			val createResponse =
				client.createContainerCmd(image).withName(config.name).withWorkingDir(config.workDir.toString())
					.withEnv(config.env.map { "${it.key}=${it.value}" }).withHostConfig(hostConfig)
					.withEntrypoint("tail", "-f", "/dev/null").exec()
			logger.info("Created container  containerId={}", createResponse.id)
			
			client.startContainerCmd(createResponse.id).exec()
			
			logger.info("Started container  containerId={}", createResponse.id)
			createResponse.id
		} catch (e: NotFoundException) {
			trace.exception(e)
			logger.warn("Failed image pull  image={}", image)
			throw ContainerOperationException("Image '$image' not found", e)
		} catch (e: CancellationException) {
			trace.exception(e)
			throw e
		} catch (e: Exception) {
			trace.exception(e)
			logger.error("Failed container start  image={}  name={}", image, config.name, e)
			throw ContainerOperationException("Failed to start container: ${e.message}", e)
		}
	}
	
	override suspend fun stop(containerId: String) = withContext(Dispatchers.IO) {
		try {
			permissionFixJob?.cancel()
			fixWorkspacePermissions(containerId)
			client.stopContainerCmd(containerId).withTimeout(10).exec()
			logger.info("Stopped container  containerId={}", containerId)
		} catch (e: NotFoundException) {
			trace.exception(e)
			logger.warn("Did not find container  containerId={}", containerId)
		} catch (e: CancellationException) {
			trace.exception(e)
			throw e
		} catch (e: Exception) {
			trace.exception(e)
			logger.error("Failed container stop  containerId={}", containerId, e)
			throw ContainerOperationException("Failed to stop container: ${e.message}", e)
		}
	}
	
	private fun fixWorkspacePermissions(containerId: String) {
		val workDir = containerWorkDir ?: return
		trace.catching {
			val execId = client.execCreateCmd(containerId)
				.withCmd("chown", "-R", uidGid, workDir.toString())
				.withAttachStdout(true)
				.withAttachStderr(true)
				.exec().id
			client.execStartCmd(execId).exec(object : ResultCallback.Adapter<Frame>() {}).awaitCompletion()
			val exitCode = client.inspectExecCmd(execId).exec().exitCodeLong?.toInt() ?: -1
			if (exitCode == 0) {
				logger.debug("Fixed workspace permissions  containerId={}", containerId)
			} else {
				logger.warn("Failed workspace permissions fix  containerId={}  exitCode={}", containerId, exitCode)
			}
		}.onFailure { e ->
			logger.warn("Failed workspace permissions fix  containerId={}", containerId, e)
		}
	}
	
	private fun schedulePermissionFix(containerId: String) {
		val delaySeconds = Settings.get(DockerSettings.PermissionFixDelaySeconds()).value
		if (delaySeconds <= 0) return
		permissionFixJob?.cancel()
		permissionFixJob = scope.launch {
			delay((delaySeconds * 1000L).milliseconds)
			fixWorkspacePermissions(containerId)
		}
	}
	
	override fun execStream(
		containerId: String, command: List<String>, workDir: Path?, env: Map<String, String>,
	): Flow<ShellEvent> = callbackFlow {
		logger.debug(
			"Started streaming exec  containerId={}  cmd={}", containerId, command.joinToString(" ")
		)
		withContext(Dispatchers.IO) {
			try {
				val execCmd =
					client.execCreateCmd(containerId).withCmd(*command.toTypedArray()).withAttachStdout(true)
						.withAttachStderr(true).withEnv(env.map { "${it.key}=${it.value}" })
				if (workDir != null) {
					execCmd.withWorkingDir(workDir.toString())
				}
				val execId = execCmd.exec().id
				
				client.execStartCmd(execId).exec(object : ResultCallback.Adapter<Frame>() {
					override fun onNext(frame: Frame) {
						val text = String(frame.payload, Charsets.UTF_8)
						when (frame.streamType) {
							StreamType.STDOUT -> trySend(ShellEvent.Stdout(text))
							StreamType.STDERR -> trySend(ShellEvent.Stderr(text))
							else -> {}
						}
					}
				}).awaitCompletion()
				
				val exitCode = client.inspectExecCmd(execId).exec().exitCodeLong?.toInt() ?: -1
				trySend(
					ShellEvent.Exit(
						ShellResult(
							exitCode = exitCode,
							timeout = exitCode == 124,
							duration = Duration.ZERO,
						)
					)
				)
			} catch (e: NotFoundException) {
				trace.exception(e)
				logger.warn("Failed container lookup  containerId={}", containerId)
				throw ContainerOperationException("Container not found: $containerId", e)
			} catch (e: CancellationException) {
				trace.exception(e)
				throw e
			} catch (e: Exception) {
				trace.exception(e)
				logger.error("Failed command execution  containerId={}", containerId, e)
				throw ContainerOperationException("Failed to exec command: ${e.message}", e)
			}
		}
		schedulePermissionFix(containerId)
		close()
	}
	
	private fun findContainerByName(name: String): ExistingContainer? {
		return client.listContainersCmd()
			.withShowAll(true)
			.withNameFilter(listOf("/$name"))
			.exec()
			.firstOrNull()
			?.let { container ->
				val details = client.inspectContainerCmd(container.id).exec()
				ExistingContainer(container.id, details.state?.status ?: "unknown")
			}
	}
	
	private data class ExistingContainer(val id: String, val state: String)
}
