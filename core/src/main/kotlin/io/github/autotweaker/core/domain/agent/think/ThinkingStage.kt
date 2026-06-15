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

import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.tool.ToolCallResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolCallValidator
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.model.Model

class ThinkingStage(
	private val llmService: LlmService,
	private val tools: Tools,
) {
	suspend fun execute(
		model: Model,
		fallbackModels: List<Model>?,
		thinking: Boolean?,
		assembledTools: List<ChatRequest.Tool>?,
		context: AgentContext,
	): Result = when (val callResult = llmService.execute(model, fallbackModels, thinking, assembledTools, context)) {
		is LlmService.CallResult.Failed -> Result.Failed
		is LlmService.CallResult.Success -> {
			val rawCalls = callResult.toolCalls
			if (rawCalls.isNullOrEmpty()) {
				return Result.Done(
					assistantMessage = callResult.assistantMessage,
					activations = emptyList(),
					parseFailures = emptyList(),
				)
			}
			
			val activations = mutableListOf<ToolActivation>()
			val parseFailures = mutableListOf<ParseFailure>()
			val needsApproval = mutableListOf<ResolvedToolCall>()
			
			val timestamp = callResult.assistantMessage.timestamp
			rawCalls.forEach { rawCall ->
				when (val result = tools.resolveToolCall(rawCall)) {
					is ToolCallResolveResult.Activation ->
						activations.add(ToolActivation(rawCall, result.message))
					
					is ToolCallResolveResult.ParseFailure ->
						parseFailures.add(ParseFailure(rawCall, result.errorMessage))
					
					is ToolCallResolveResult.NeedsApproval -> {
						val validatedArgs =
							tools.serializeValidatedArgs(result.result.toolName, result.result.args)
						val pendingCall = AgentContext.CurrentRound.PendingToolCall(
							callId = rawCall.id,
							name = rawCall.name,
							arguments = rawCall.arguments,
							reason = result.result.reason,
							timestamp = timestamp,
							validatedArgs = validatedArgs,
						)
						needsApproval.add(ResolvedToolCall(pendingCall, result.result))
					}
				}
			}
			
			if (needsApproval.isNotEmpty()) {
				Result.HasPending(
					assistantMessage = callResult.assistantMessage,
					activations = activations,
					parseFailures = parseFailures,
					needsApproval = needsApproval,
				)
			} else {
				Result.Done(
					assistantMessage = callResult.assistantMessage,
					activations = activations,
					parseFailures = parseFailures,
				)
			}
		}
	}
	
	
	sealed class Result {
		data class Done(
			val assistantMessage: AgentContext.Message.Assistant,
			val activations: List<ToolActivation>,
			val parseFailures: List<ParseFailure>,
		) : Result()
		
		data class HasPending(
			val assistantMessage: AgentContext.Message.Assistant,
			val activations: List<ToolActivation>,
			val parseFailures: List<ParseFailure>,
			val needsApproval: List<ResolvedToolCall>,
		) : Result()
		
		data object Failed : Result()
	}
	
	class ParseFailure(
		val toolCall: ChatMessage.AssistantMessage.ToolCall,
		val errorMessage: String,
	)
	
	class ResolvedToolCall(
		val pendingCall: AgentContext.CurrentRound.PendingToolCall,
		val validated: ToolCallValidator.ValidationResult.Success<out ToolArgs>,
	)
}
