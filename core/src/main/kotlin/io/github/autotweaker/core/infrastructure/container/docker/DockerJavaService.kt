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
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.infrastructure.container.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue
import java.time.Duration as JavaDuration

class DockerJavaService : ContainerService, Loggable, Traceable {
	private val uidGid: String by lazy {
		val unix = UnixSystem()
		"${unix.uid}:${unix.gid}"
	}
	
	@Volatile
	private var permissionFixJob: Job? = null
	private val lock = ReentrantMutex()
	
	private val scope = scope(IO)
	
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
		return true
	}.getOrDefault(false)
	
	private fun tmpfsOptions(): String =
		"rw,nosuid,nodev,exec,relatime,mode=1777,size=${ContainerSettings.ContainerTmpSizeMb().get()}m"
	
	override suspend fun pull(image: String) = withContext(Dispatchers.IO) {
		client.pullImageCmd(image).exec(object : PullImageResultCallback() {}).awaitCompletion()
		log.info("Pulled image  image={}", image)
	}
	
	override suspend fun start(
		image: String,
	): String = withContext(Dispatchers.IO) {
		trace.catching {
			Files.createDirectories(WORKSPACE_HOST_PATH)
			Files.createDirectories(TMP_HOST_PATH)
			
			val existing = findContainerByName(CONTAINER_NAME)
			if (existing != null) {
				if (existing.state != "running") {
					client.startContainerCmd(existing.id).exec()
					log.info("Restarted container  containerId={}", existing.id)
				} else {
					log.debug("Found container already running  containerId={}", existing.id)
				}
				return@withContext existing.id
			}
			
			val hostConfig = HostConfig().withBinds(
				Bind(WORKSPACE_HOST_PATH.toString(), Volume(CONTAINER_WORK_PATH.toString())),
				Bind(TMP_HOST_PATH.toString(), Volume(CONTAINER_TMP_PATH.toString()))
			).withExtraHosts("host.docker.internal:host-gateway").withInit(true)
				.withTmpFs(mapOf("/tmp" to tmpfsOptions()))
			
			val createResponse = client.createContainerCmd(image)
				.withName(CONTAINER_NAME)
				.withWorkingDir(CONTAINER_WORK_PATH.toString())
				.withHostConfig(hostConfig)
				.withEntrypoint("tail", "-f", "/dev/null")
				.exec().andLog(log) { info("Created container  containerId={}", it.id) }
			
			client.startContainerCmd(createResponse.id).exec()
			
			log.info("Started container  containerId={}", createResponse.id)
			createResponse.id
		}.rethrowCancellation()
			.recoverException { e: NotFoundException ->
				log.warn("Failed image pull  image={}", image)
				throw ContainerOperationException("Image '$image' not found", e)
			}.getOrElse { e ->
				log.error("Failed container start  image={}  name={}", image, CONTAINER_NAME, e)
				throw ContainerOperationException("Failed to start container: ${e.message()}", e)
			}
	}
	
	override suspend fun stop(containerId: String) = withContext(Dispatchers.IO) {
		trace.catching {
			lock.withLock { permissionFixJob?.cancel() }
			fixWorkspacePermissions(containerId)
			client.stopContainerCmd(containerId).withTimeout(10).exec()
			log.info("Stopped container  containerId={}", containerId)
		}.rethrowCancellation()
			.recoverException { _: NotFoundException ->
				log.warn(
					"Did not find container  containerId={}",
					containerId
				)
			}
			.onFailure { e ->
				log.error("Failed container stop  containerId={}", containerId, e)
				throw ContainerOperationException("Failed to stop container: ${e.message()}", e)
			}.discard()
	}
	
	private fun fixWorkspacePermissions(containerId: String) {
		listOf(CONTAINER_WORK_PATH, CONTAINER_TMP_PATH).forEach { dir ->
			trace.catching {
				val execId = client.execCreateCmd(containerId)
					.withCmd("chown", "-R", uidGid, dir.toString())
					.withAttachStdout(true)
					.withAttachStderr(true)
					.exec().id
				client.execStartCmd(execId).exec(object : ResultCallback.Adapter<Frame>() {}).awaitCompletion()
				val exitCode = client.inspectExecCmd(execId).exec().exitCodeLong?.toInt() ?: -1
				if (exitCode == 0) {
					log.debug("Fixed workspace permissions  containerId={}  dir={}", containerId, dir)
				} else {
					log.warn(
						"Failed workspace permissions fix  containerId={}  dir={}  exitCode={}",
						containerId,
						dir,
						exitCode
					)
				}
			}.onFailure { e ->
				log.warn("Failed workspace permissions fix  containerId={}  dir={}", containerId, dir, e)
			}
		}
	}
	
	private suspend fun schedulePermissionFix(containerId: String) {
		val delaySeconds = DockerSettings.PermissionFixDelaySeconds().get()
		if (delaySeconds <= 0) return
		lock.withLock {
			permissionFixJob?.cancel()
			permissionFixJob = scope.launch {
				delay(delaySeconds.seconds)
				fixWorkspacePermissions(containerId)
			}
		}
	}
	
	override fun exec(
		containerId: String, command: List<String>, workDir: Path?, env: Map<String, String>,
		timeout: Duration,
	): Flow<ShellEvent> = callbackFlow {
		val wrappedCommand = listOf(
			"timeout", "--kill-after=${DockerSettings.KillAfterSeconds().get()}",
			"${timeout.inWholeSeconds}"
		) + command
		log.debug(
			"Started streaming exec  containerId={}  cmd={}", containerId,
			wrappedCommand.joinToString(SPACE.toString())
		)
		withContext(Dispatchers.IO) {
			var execId: String? = null
			trace.catching {
				val execCmd =
					client.execCreateCmd(containerId).withCmd(*wrappedCommand.toTypedArray()).withAttachStdout(true)
						.withAttachStderr(true).withEnv(env.map { "${it.key}=${it.value}" })
				if (workDir != null)
					execCmd.withWorkingDir(workDir.toString())
				
				execId = execCmd.exec().id
				
				val execDuration = measureTimedValue {
					suspendCancellableCoroutine { cont ->
						val callback = client.execStartCmd(execId).exec(object : ResultCallback.Adapter<Frame>() {
							override fun onNext(frame: Frame) {
								val text = String(frame.payload, Charsets.UTF_8)
								when (frame.streamType) {
									StreamType.STDOUT -> trySend(ShellEvent.Stdout(text))
									StreamType.STDERR -> trySend(ShellEvent.Stderr(text))
									else -> {}
								}
							}
							
							override fun onComplete() {
								cont.resume(client.inspectExecCmd(execId).exec().exitCodeLong?.toInt() ?: -1)
							}
							
							override fun onError(throwable: Throwable) {
								cont.resumeWithException(throwable)
							}
						})
						cont.invokeOnCancellation {
							callback.close()
							execId?.let {
								scope.launch {
									withContext(NonCancellable + Dispatchers.IO) {
										killExecProcess(containerId, it)
									}
								}
							}
						}
					}
				}
				trySend(
					ShellEvent.Exit(
						ShellResult(
							exitCode = execDuration.value,
							timeout = execDuration.value == 124,
							duration = execDuration.duration,
						)
					)
				)
			}.rethrowCancellation {
				execId?.let { killExecProcess(containerId, it) }
			}.recoverException { e: NotFoundException ->
				log.warn("Failed container lookup  containerId={}  reason={}", containerId, e.message)
				throw ContainerOperationException("Container not found: $containerId", e)
			}.onFailure { e ->
				log.error("Failed command execution  containerId={}", containerId, e)
				throw ContainerOperationException("Failed to exec command: ${e.message()}", e)
			}.getOrThrow()
		}
		schedulePermissionFix(containerId)
		close()
	}
	
	private fun killExecProcess(containerId: String, execId: String) {
		trace.catching {
			val pid = client.inspectExecCmd(execId).exec().pidLong ?: return@catching
			val killExecId = client.execCreateCmd(containerId)
				.withCmd("bash", "-lc", "kill -KILL -- -$pid 2>/dev/null")
				.exec().id
			client.execStartCmd(killExecId).exec(object : ResultCallback.Adapter<Frame>() {})
				.awaitCompletion(10, TimeUnit.SECONDS)
		}.onFailure { e ->
			log.debug("Failed exec kill  containerId={}  execId={}", containerId, execId, e)
		}
	}
	
	private fun findContainerByName(name: String): ExistingContainer? =
		client.listContainersCmd()
			.withShowAll(true)
			.withNameFilter(listOf("/$name"))
			.exec()
			.firstOrNull()
			?.let { container ->
				val details = client.inspectContainerCmd(container.id).exec()
				ExistingContainer(container.id, details.state?.status ?: "unknown")
			}
	
	private data class ExistingContainer(val id: String, val state: String)
}
