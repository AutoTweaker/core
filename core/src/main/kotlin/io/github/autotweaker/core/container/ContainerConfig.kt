package io.github.autotweaker.core.container

import java.nio.file.Path
import java.nio.file.Paths

data class ContainerConfig(
	val name: String = "autotweaker",
	val env: Map<String, String> = emptyMap(),
	val workDir: Path = Paths.get("/workspace"),
	val workspaceHostPath: Path = Paths.get("~/.config/autotweaker/container/workspace"),
)
