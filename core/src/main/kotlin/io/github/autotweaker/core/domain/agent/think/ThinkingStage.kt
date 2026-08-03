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
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeContext.CurrentRound.PendingToolCall
import io.github.autotweaker.core.domain.agent.RuntimeOutput
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.tool.ToolCallParser
import io.github.autotweaker.core.domain.agent.tool.ToolCallResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.agent.tool.Tools
import io.github.autotweaker.core.domain.tool.port.TruncationService
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import java.util.*

class ThinkingStage(
	private val llmService: LlmService,
	private val tools: Tools,
	private val workspace: () -> Path,
	private val truncation: TruncationService,
	private val onOutput: (RuntimeOutput) -> Unit,
) {
	suspend fun execute(
		model: AgentModel,
		assembledTools: List<ChatRequest.Tool>?,
		context: RuntimeContext,
	): Result = when (val callResult = llmService.execute(model, assembledTools, context)) {
		is LlmService.CallResult.Failed -> Result.Failed
		is LlmService.CallResult.Success -> {
			val rawCalls = callResult.toolCalls
			if (rawCalls.isNullOrEmpty()) return Result.Done(
				assistantMessage = callResult.assistantMessage,
				activations = emptyList(),
				parseFailures = emptyList(),
				resolveFailures = emptyList()
			)
			
			
			val activations = mutableListOf<ToolActivation>()
			val parseFailures = mutableListOf<ParseFailure>()
			val resolveFailures = mutableListOf<ResolveFailure>()
			val needsApproval = mutableListOf<ResolvedToolCall>()
			
			val timestamp = callResult.assistantMessage.timestamp
			val provider = ToolProvider.buildToolProvider(
				workspace = workspace,
				onOutput = onOutput,
				model = model,
				context = context,
				truncation = truncation,
			)
			rawCalls.forEach { rawCall ->
				when (val result = tools.resolveToolCall(rawCall, provider)) {
					is ToolCallResolveResult.Activation ->
						activations.add(ToolActivation(rawCall, result.message))
					
					is ToolCallResolveResult.ParseFailure ->
						parseFailures.add(ParseFailure(rawCall, result.errorMessage))
					
					is ToolCallResolveResult.ResolveFailure -> resolveFailures.add(
						ResolveFailure(
							rawCall,
							result.result.reason,
							result.result.toolName,
							Tools.serializeValidatedArgs(result.result.toolName, result.result.args),
							result.errorMessage
						)
					)
					
					is ToolCallResolveResult.NeedsApproval -> {
						val validatedArgs =
							Tools.serializeValidatedArgs(result.result.toolName, result.result.args)
						val pendingCall = PendingToolCall(
							id = UUID.randomUUID(),
							timestamp = timestamp,
							callId = rawCall.id,
							callName = rawCall.name,
							arguments = rawCall.arguments,
							reason = result.result.reason,
							validatedToolName = result.result.toolName,
							validatedArgs = validatedArgs,
							resolvedRequest = result.request
						)
						needsApproval.add(ResolvedToolCall(pendingCall, result.result))
					}
				}
			}
			
			if (needsApproval.isNotEmpty()) Result.HasPending(
				assistantMessage = callResult.assistantMessage,
				activations = activations,
				parseFailures = parseFailures,
				resolveFailures = resolveFailures,
				needsApproval = needsApproval,
			)
			else Result.Done(
				assistantMessage = callResult.assistantMessage,
				activations = activations,
				parseFailures = parseFailures,
				resolveFailures = resolveFailures
			)
		}
	}
	
	
	sealed class Result {
		data class Done(
			val assistantMessage: RuntimeContext.Message.Assistant,
			val activations: List<ToolActivation>,
			val parseFailures: List<ParseFailure>,
			val resolveFailures: List<ResolveFailure>,
		) : Result()
		
		data class HasPending(
			val assistantMessage: RuntimeContext.Message.Assistant,
			val activations: List<ToolActivation>,
			val parseFailures: List<ParseFailure>,
			val resolveFailures: List<ResolveFailure>,
			val needsApproval: List<ResolvedToolCall>,
		) : Result()
		
		data object Failed : Result()
	}
	
	class ParseFailure(
		val toolCall: ChatMessage.AssistantMessage.ToolCall,
		val errorMessage: String,
	)
	
	class ResolveFailure(
		val toolCall: ChatMessage.AssistantMessage.ToolCall,
		val reason: String,
		val validatedToolName: String,
		val validatedArgs: JsonElement,
		val errorMessage: String,
	)
	
	class ResolvedToolCall(
		val pendingCall: PendingToolCall,
		val validated: ToolCallParser.ValidationResult.Success<out ToolArgs>,
	)
}
