package io.github.autotweaker.core.tool.impl.read

import kotlinx.serialization.Serializable

@Serializable
data class AutoApprovalRule(
	val workspaceName: String?,
	val globPattern: List<String>,
	val excludeGlob: List<String>,
)