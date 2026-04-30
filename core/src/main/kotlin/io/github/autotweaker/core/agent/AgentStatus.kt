package io.github.autotweaker.core.agent


enum class AgentStatus {
	FREE,
	PROCESSING,
	RETRYING,
	TOOL_CALLING,
	WAITING,
	
	@Suppress("unused")
	COMPACTING,
	ERROR
}
