package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.Usage
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant

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
    ) : AgentChatStreamResult()

    data class Outputting(
        val reasoningContent: String?,
        val content: String
    ) : AgentChatStreamResult()

    data class Finished(
        val result: AgentChatResult
    ) : AgentChatStreamResult()
}

data class Error(
    val content: String?,
    val statusCode: HttpStatusCode?,
    val retrying: Model?,
    val timestamp: Instant,
)
