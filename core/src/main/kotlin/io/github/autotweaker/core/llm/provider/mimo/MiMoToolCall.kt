package io.github.autotweaker.core.llm.provider.mimo

import kotlinx.serialization.Serializable

@Serializable
data class MiMoToolCall(
	val id: String,
	val type: String = "function",
	val function: Function
) {
	@Serializable
	data class Function(
		val name: String,
		val arguments: String
	)
}
