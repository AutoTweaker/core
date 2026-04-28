package io.github.autotweaker.core.container

data class CommandResult(
	val exitCode: Int,
	val stdout: String,
	val stderr: String,
)
