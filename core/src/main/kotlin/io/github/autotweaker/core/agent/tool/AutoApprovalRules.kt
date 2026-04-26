package io.github.autotweaker.core.agent.tool

data class AutoApprovalRules(
	val read: Read? = null,
) {
	data class Read(
		val globPattern: List<String>,
	)
}
