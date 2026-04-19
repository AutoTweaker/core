package io.github.autotweaker.core.agent

@Suppress("unused")
enum class AgentStatus {
	FREE,
	PROCESSING,
	RETRYING,
	TOOL_CALLING,
	WAITING,
	COMPACTING,
	ERROR
}
