package io.github.autotweaker.core.tool.impl.bash

interface BashService {
	suspend fun run(command: String, timeoutSeconds: Int, env: Map<String, String>): Result
	
	data class Result(
		val exitCode: Int,
		val stdout: String,
		val stderr: String,
		val timeout: Boolean,
		val durationSeconds: Double,
	)
}
