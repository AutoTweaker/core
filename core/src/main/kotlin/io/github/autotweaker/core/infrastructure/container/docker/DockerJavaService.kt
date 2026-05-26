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
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DockerClientImpl
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.github.autotweaker.core.infrastructure.container.ContainerOperationException
import io.github.autotweaker.core.infrastructure.container.ContainerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class DockerJavaService : ContainerService {
	
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	@Suppress("DEPRECATION")
	private val client: DockerClient = DockerClientImpl.getInstance()
	
	override fun shutdown() {
		runCatching { client.close() }
	}
	
	override suspend fun start(image: String, config: ContainerConfig): String = withContext(Dispatchers.IO) {
		try {
			client.pullImageCmd(image).exec(object : PullImageResultCallback() {}).awaitCompletion()
			logger.info("Pulled image  image={}", image)
			
			val workspaceHostPath = config.workspaceHostPath
			Files.createDirectories(workspaceHostPath)
			
			val hostConfig = HostConfig().withBinds(
				Bind(
					workspaceHostPath.toString(), Volume(config.workDir.toString())
				)
			)
			
			val createResponse =
				client.createContainerCmd(image).withName(config.name).withWorkingDir(config.workDir.toString())
					.withEnv(config.env.map { "${it.key}=${it.value}" }).withHostConfig(hostConfig)
					.withEntrypoint("tail", "-f", "/dev/null").exec()
			logger.info("Container created  containerId={}", createResponse.id)
			
			client.startContainerCmd(createResponse.id).exec()
			
			logger.info("Container started  containerId={}", createResponse.id)
			createResponse.id
		} catch (e: ConflictException) {
			logger.warn("Container name already used  name={}", config.name)
			throw ContainerOperationException("Container '${config.name}' already exists", e)
		} catch (e: NotFoundException) {
			logger.warn("Failed to pull image  image={}", image)
			throw ContainerOperationException("Image '$image' not found", e)
		} catch (e: Exception) {
			logger.error("Failed to start container  image={}  name={}", image, config.name, e)
			throw ContainerOperationException("Failed to start container: ${e.message}", e)
		}
	}
	
	override suspend fun stop(containerId: String) = withContext(Dispatchers.IO) {
		try {
			client.stopContainerCmd(containerId).withTimeout(10).exec()
			logger.debug("Container stopped  containerId={}", containerId)
			client.removeContainerCmd(containerId).exec()
			logger.info("Container removed  containerId={}", containerId)
		} catch (_: NotFoundException) {
			logger.warn("Container already removed  containerId={}", containerId)
		} catch (e: Exception) {
			logger.error("Failed to stop container  containerId={}", containerId, e)
			throw ContainerOperationException("Failed to stop container: ${e.message}", e)
		}
	}
	
	override fun execStream(
		containerId: String, command: List<String>, workDir: Path?, timeout: Duration, env: Map<String, String>,
	): Flow<ShellEvent> = callbackFlow {
		logger.debug(
			"Streaming exec started  containerId={}  cmd={}", containerId, command.joinToString(" ")
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
				
				val completed = client.execStartCmd(execId).exec(object : ResultCallback.Adapter<Frame>() {
					override fun onNext(frame: Frame) {
						val text = String(frame.payload, Charsets.UTF_8)
						when (frame.streamType) {
							StreamType.STDOUT -> trySend(ShellEvent.Stdout(text))
							StreamType.STDERR -> trySend(ShellEvent.Stderr(text))
							else -> {}
						}
					}
				}).awaitCompletion(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
				
				val exitCode = if (completed) client.inspectExecCmd(execId).exec().exitCodeLong else null
				trySend(
					ShellEvent.Exit(
						ShellResult(
							exitCode = exitCode?.toInt() ?: -1,
							timeout = !completed,
							duration = Duration.ZERO,
						)
					)
				)
			} catch (e: NotFoundException) {
				logger.warn("Failed to find container  containerId={}", containerId)
				throw ContainerOperationException("Container not found: $containerId", e)
			} catch (e: Exception) {
				logger.error("Failed to exec command  containerId={}", containerId, e)
				throw ContainerOperationException("Failed to exec command: ${e.message}", e)
			}
		}
		close()
	}
}
