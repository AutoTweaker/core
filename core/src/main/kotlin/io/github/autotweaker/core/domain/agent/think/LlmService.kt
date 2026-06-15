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

import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.chat.AgentChat
import io.github.autotweaker.core.domain.agent.chat.AgentChatRequest
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.*

class LlmService(
	private val agentId: UUID,
	private val onOutput: suspend (AgentOutput) -> Unit,
) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	
	suspend fun execute(
		model: Model,
		fallbackModels: List<Model>?,
		thinking: Boolean?,
		assembledTools: List<ChatRequest.Tool>?,
		context: AgentContext,
	): CallResult {
		val request = AgentChatRequest(
			model = model,
			fallbackModels = fallbackModels,
			thinking = thinking,
			tools = assembledTools,
			context = context,
		)
		
		return try {
			runStream(request)
		} catch (e: CancellationException) {
			trace.exception(e)
			logger.debug("Cancelled LLM call  agentId={}", agentId)
			CallResult.Failed
		} catch (e: Exception) {
			trace.exception(e)
			logger.error("Failed LLM call  agentId={}", agentId, e)
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
		
		logger.info(
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
