package io.github.autotweaker.core.container

interface ContainerService {
	suspend fun start(image: String, config: ContainerConfig): String
	suspend fun stop(containerId: String)
	suspend fun exec(
		containerId: String,
		command: List<String>,
		workDir: String? = null,
		timeoutSeconds: Long = 30,
	): CommandResult
}
