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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.types.agent.AgentOutput
import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.chat.AgentChat
import io.github.autotweaker.core.domain.agent.chat.AgentChatRequest
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

class LlmService(
	private val chat: AgentChat,
	private val agentId: UUID,
	private val status: MutableStateFlow<AgentStatus>,
	private val onOutput: (RuntimeOutput) -> Unit,
) : Loggable, Traceable {
	suspend fun execute(
		model: AgentModel,
		assembledTools: List<ChatRequest.Tool>?,
		context: RuntimeContext,
	): CallResult? { // null == failed
		val request = AgentChatRequest(
			model = model,
			tools = assembledTools,
			context = context,
		)
		
		return trace.catching {
			runStream(request)
		}.rethrow<SecretStoreLockedException>()
			.rethrowCancellation {
				log.debug("Cancelled LLM call  agentId={}", agentId)
			}.getOrElse { e ->
				log.error("Failed LLM call  agentId={}", agentId, e)
				onOutput(
					RuntimeOutput.Output(
						AgentOutput.Error(
							e.message(),
							AgentOutput.Error.Type.LLM,
						)
					)
				)
				return@getOrElse null
			}
	}
	
	private suspend fun runStream(request: AgentChatRequest): CallResult {
		var assembled: AgentChatStreamResult.Assembled? = null
		
		status.value = AgentStatus.THINKING
		chat.execute(request, agentId).collect { result ->
			when (result) {
				is AgentChatStreamResult.Delta -> {
					onOutput(RuntimeOutput.Output(result.delta))
				}
				
				is AgentChatStreamResult.Failing -> {
					onOutput(
						RuntimeOutput.Output(
							AgentOutput.LlmError(
								result.error,
								result.statusCode,
								result.exception,
								result.model
							)
						)
					)
				}
				
				is AgentChatStreamResult.Assembled -> {
					assembled = result
				}
			}
		}
		
		val final = assembled ?: error("Stream ended without assembled result")
		
		log.info(
			"Completed LLM call  agentId={}  model={}  charCount={}",
			agentId, final.message.modelId, final.message.content?.length ?: 0
		)
		
		return CallResult(
			assistantMessage = final.message,
			toolCalls = final.toolCalls,
		)
	}
	
	data class CallResult(
		val assistantMessage: RuntimeContext.Message.Assistant,
		val toolCalls: List<ChatMessage.Assistant.ToolCall>?,
	)
}
