package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatResult
import io.ktor.http.*
import kotlin.time.Instant

data class AgentChatRequest(
	val model: Model,
	val fallbackModels: List<Model>?,
	val thinking: Boolean?,
	val tools: List<ChatRequest.Tool>?,
	
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
			val finishReason: ChatResult.FinishReason?,
		)
	}
}
