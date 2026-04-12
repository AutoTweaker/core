package io.github.whiteelephant.autotweaker.core.agent

enum class AgentStatus {
    FREE,
    PROCESSING,
    RETRYING,
    TOOL_CALLING,
    WAITING,
    COMPACTING,
    ERROR
}
