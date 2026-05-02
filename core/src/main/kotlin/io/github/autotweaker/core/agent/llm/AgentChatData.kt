/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
