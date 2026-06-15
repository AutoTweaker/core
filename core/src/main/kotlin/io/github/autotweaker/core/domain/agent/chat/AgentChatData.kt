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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentModel
import java.util.*
import kotlin.time.Instant

data class AgentChatRequest(
	val model: AgentModel,
	val tools: List<ChatRequest.Tool>?,
	val context: AgentContext,
)

sealed class AgentChatStreamResult {
	data class Delta(
		val delta: StreamDelta
	) : AgentChatStreamResult()
	
	data class Failing(
		val errors: List<Error>
	) : AgentChatStreamResult() {
		data class Error(
			val content: String?,
			val statusCode: Int?,
			val model: UUID,
			val timestamp: Instant,
			val usage: Usage? = null,
		)
	}
	
	data class Assembled(
		val message: AgentContext.Message.Assistant,
		val toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
		val finishReason: ChatResult.FinishReason?,
	) : AgentChatStreamResult()
}
