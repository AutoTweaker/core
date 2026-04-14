package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.Usage
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant

data class AgentChatRequest(
    val model: Model,
    val fallbackModels: List<Model>?,
    val thinking: Boolean?,
    val tools: List<ChatRequest.Tool>?,

    val maxTokens: Int? = null,
    val temperature: Double? = null,

    val context: AgentContext,
)

sealed class AgentChatStreamResult {
    data class Failing(
        val errors: List<Error>
    ) : AgentChatStreamResult() {
        data class Error(
            val content: String?,
            val statusCode: HttpStatusCode?,
            val retrying: Model?,
            val timestamp: Instant,
        )
    }

    data class Reasoning(
        val reasoningContent: String
    ) : AgentChatStreamResult()

    data class Outputting(
        val reasoningContent: String?,
        val content: String
    ) : AgentChatStreamResult()

    data class Finished(
        val result: Result
    ) : AgentChatStreamResult() {
        data class Result(
            val context: AgentContext.Message.Assistant,
            val toolCalls: List<AgentContext.CurrentRound.PendingToolCall>?,
            val usage: Usage?,
            val finishReason: ChatResult.FinishReason?,
        )
    }
}
