package io.github.autotweaker.core.container

data class ContainerConfig(
	val name: String = "autotweaker",
	val env: Map<String, String> = emptyMap(),
	val workDir: String = "/workspace",
	val workspaceHostPath: String = "~/.config/autotweaker/container/workspace",
)
