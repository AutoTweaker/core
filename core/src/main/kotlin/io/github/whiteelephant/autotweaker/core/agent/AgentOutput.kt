package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext

sealed class AgentOutput {
    data class Messages(
        val compactedRounds: List<CompactedRound>?,
        val messages: List<AgentContext.Message>,
    ) : AgentOutput() {
        data class CompactedRound(
            val messages: List<AgentContext.Message>,
            val summarizedMessages: String?,
        )
    }

    data class ToolCallRequest(
        val pendingToolCalls: List<AgentContext.CurrentRound.PendingToolCall>,
    ) : AgentOutput()

    data class Error(
        val message: String,
        val type: Type,
    ) : AgentOutput() {
        enum class Type {
            NETWORK,
            LLM,
            TOOL,
            UNKNOWN
        }
    }
}
