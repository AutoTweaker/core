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

package io.github.autotweaker.core.domain.agent.think

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.getOrElse
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.chat.AgentChat
import io.github.autotweaker.core.domain.agent.chat.AgentChatRequest
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import java.util.*

class LlmService(
	private val agentId: UUID,
	private val onOutput: suspend (AgentOutput) -> Unit,
) : Loggable, Traceable {
	suspend fun execute(
		model: AgentModel,
		assembledTools: List<ChatRequest.Tool>?,
		context: AgentContext,
	): CallResult {
		val request = AgentChatRequest(
			model = model,
			tools = assembledTools,
			context = context,
		)
		
		return trace.catching {
			runStream(request)
		}.rethrowCancellation {
			log.debug("Cancelled LLM call  agentId={}", agentId)
		}.getOrElse { e ->
			log.error("Failed LLM call  agentId={}", agentId, e)
			onOutput(
				AgentOutput.Error(
					io.github.autotweaker.api.types.agent.AgentError(
						e.message ?: "LLM call failed",
						io.github.autotweaker.api.types.agent.AgentError.Type.LLM,
					)
				)
			)
			CallResult.Failed
		}
	}
	
	private suspend fun runStream(request: AgentChatRequest): CallResult {
		var assembled: AgentChatStreamResult.Assembled? = null
		
		AgentChat.execute(request, agentId).collect { result ->
			when (result) {
				is AgentChatStreamResult.Delta -> {
					onOutput(AgentOutput.LlmDelta(result.delta))
				}
				
				is AgentChatStreamResult.Failing -> {
					val lastError = result.errors.lastOrNull()
						?: error("Failing event with empty error list")
					onOutput(AgentOutput.LlmError(lastError))
				}
				
				is AgentChatStreamResult.Assembled -> {
					assembled = result
				}
			}
		}
		
		val final = assembled
			?: error("Stream ended without assembled result")
		
		log.info(
			"Completed LLM call  agentId={}  model={}  charCount={}",
			agentId, final.message.modelId, final.message.content?.length ?: 0
		)
		
		return CallResult.Success(
			assistantMessage = final.message,
			toolCalls = final.toolCalls,
		)
	}
	
	sealed class CallResult {
		data class Success(
			val assistantMessage: AgentContext.Message.Assistant,
			val toolCalls: List<ChatMessage.AssistantMessage.ToolCall>?,
		) : CallResult()
		
		data object Failed : CallResult()
	}
}
