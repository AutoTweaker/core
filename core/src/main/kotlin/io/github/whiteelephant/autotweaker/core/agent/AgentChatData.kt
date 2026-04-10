package io.github.whiteelephant.autotweaker.core.agent

import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.Usage

data class AgentChatRequest(
    val model: Model,
    val thinking: Boolean?,
    val tools: List<ChatRequest.Tool>?,

    val maxTokens: Int? = null,
    val temperature: Double? = null,

    val context: AgentContext
)

data class AgentChatResult(
    val context: AgentContext.Message.Assistant,
    val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>?,
    val usage: Usage?,
    val finishReason: ChatResult.FinishReason?,
)

sealed class AgentChatStreamResult {
    data class Reasoning(
        val reasoningContent: String
    )

    data class Outputting(
        val reasoningContent: String?,
        val content: String
    )

    data class Finished(
        val result: AgentChatResult
    )
}
