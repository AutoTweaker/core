package io.github.autotweaker.core.container.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.core.DockerClientImpl
import io.github.autotweaker.core.container.CommandResult
import io.github.autotweaker.core.container.ContainerConfig
import io.github.autotweaker.core.container.ContainerOperationException
import io.github.autotweaker.core.container.ContainerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DockerJavaService : ContainerService {
	
	private val logger = LoggerFactory.getLogger(DockerJavaService::class.java)
	
	@Suppress("DEPRECATION")
	private val client: DockerClient = DockerClientImpl.getInstance()
	
	override suspend fun start(image: String, config: ContainerConfig): String =
		withContext(Dispatchers.IO) {
			try {
				logger.info("Pulling image: $image")
				client.pullImageCmd(image)
					.exec(object : PullImageResultCallback() {})
					.awaitCompletion()
				
				val workspaceHostPath = config.workspaceHostPath
				Files.createDirectories(workspaceHostPath)

				val hostConfig = HostConfig()
					.withBinds(
						com.github.dockerjava.api.model.Bind(
							workspaceHostPath.toString(),
							com.github.dockerjava.api.model.Volume(config.workDir.toString())
						)
					)

				logger.info("Creating container: ${config.name}")
				val createResponse = client.createContainerCmd(image)
					.withName(config.name)
					.withWorkingDir(config.workDir.toString())
					.withEnv(config.env.map { "${it.key}=${it.value}" })
					.withHostConfig(hostConfig)
					.withEntrypoint("tail", "-f", "/dev/null")
					.exec()
				
				logger.info("Starting container: ${createResponse.id}")
				client.startContainerCmd(createResponse.id).exec()
				
				createResponse.id
			} catch (e: ConflictException) {
				throw ContainerOperationException("Container '${config.name}' already exists", e)
			} catch (e: NotFoundException) {
				throw ContainerOperationException("Image '$image' not found", e)
			} catch (e: Exception) {
				throw ContainerOperationException("Failed to start container: ${e.message}", e)
			}
		}
	
	override suspend fun stop(containerId: String) = withContext(Dispatchers.IO) {
		try {
			logger.info("Stopping container: $containerId")
			client.stopContainerCmd(containerId)
				.withTimeout(10)
				.exec()
			client.removeContainerCmd(containerId).exec()
			logger.info("Container removed: $containerId")
		} catch (_: NotFoundException) {
			logger.warn("Container not found, already removed: $containerId")
		} catch (e: Exception) {
			throw ContainerOperationException("Failed to stop container: ${e.message}", e)
		}
	}
	
	override suspend fun exec(
		containerId: String,
		command: List<String>,
		workDir: String?,
		timeoutSeconds: Long,
	): CommandResult = withContext(Dispatchers.IO) {
		try {
			val execCmd = client.execCreateCmd(containerId)
				.withCmd(*command.toTypedArray())
				.withAttachStdout(true)
				.withAttachStderr(true)
			if (workDir != null) {
				execCmd.withWorkingDir(workDir)
			}
			val execId = execCmd.exec().id
			
			val stdout = StringBuilder()
			val stderr = StringBuilder()
			
			client.execStartCmd(execId)
				.exec(object : ResultCallback.Adapter<Frame>() {
					override fun onNext(frame: Frame) {
						when (frame.streamType) {
							StreamType.STDOUT -> stdout.append(String(frame.payload, Charsets.UTF_8))
							StreamType.STDERR -> stderr.append(String(frame.payload, Charsets.UTF_8))
							else -> {}
						}
					}
				})
				.awaitCompletion(timeoutSeconds, TimeUnit.SECONDS)
			
			val exitCode = client.inspectExecCmd(execId).exec().exitCodeLong
			
			CommandResult(
				exitCode = exitCode?.toInt() ?: -1,
				stdout = stdout.toString(),
				stderr = stderr.toString(),
			)
		} catch (e: NotFoundException) {
			throw ContainerOperationException("Container not found: $containerId", e)
		} catch (e: Exception) {
			throw ContainerOperationException("Failed to exec command: ${e.message}", e)
		}
	}
}
