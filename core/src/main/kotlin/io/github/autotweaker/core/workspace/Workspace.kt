package io.github.autotweaker.core.workspace

import java.nio.file.Path

data class Workspace(
	val name: String,
	val inContainer: Boolean,
	val path: Path
)