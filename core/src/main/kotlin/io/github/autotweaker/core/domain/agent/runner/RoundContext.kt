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

package io.github.autotweaker.core.domain.agent.runner

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.MessageBuilder
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolSettings.ACTIVE_TOOL_NAME
import kotlinx.serialization.json.JsonElement

class RoundContext(
	private val ctx: ContextManager,
	private val msg: MessageBuilder
) {
	suspend fun applyThinking(
		thinking: ThinkingStage.Result,
	) = ctx.applyThinking(
		assistantRef = thinking.assistantMessage.ref(),
		toolCalls = thinking.toolCalls?.map { (call, resolved) ->
			var result: AgentMessage.Tool.Result? = null
			var ready: Tool.ResolveResult.Ready? = null
			
			suspend fun toolCall(
				reason: String?,
				validatedToolName: String?,
				validatedArgs: JsonElement?,
				resolvedRequest: JsonElement?,
				presentation: ToolPresentation?,
			) = msg.toolCall(
				timestamp = thinking.assistantMessage.timestamp,
				callId = call.id,
				callName = call.name,
				arguments = call.arguments,
				reason = reason,
				validatedToolName = validatedToolName,
				validatedArgs = validatedArgs,
				resolvedRequest = resolvedRequest,
				presentation = presentation,
			)
			
			suspend fun toolResult(
				content: String,
				presentation: ToolPresentation,
				status: ToolResultStatus
			) = msg.toolResult(
				callId = call.id,
				content = content,
				data = null,
				presentation = presentation,
				status = status,
			)
			
			val call = when (resolved) {
				is ResolveResult.ParseFailure -> toolCall(
					reason = null,
					validatedToolName = null,
					validatedArgs = null,
					resolvedRequest = null,
					presentation = null
				).also {
					result = toolResult(
						content = resolved.errorMessage,
						presentation = resolved.presentation,
						status = ToolResultStatus.FAILURE,
					)
				}
				
				is ResolveResult.ResolveFailure -> toolCall(
					reason = resolved.reason,
					validatedToolName = resolved.toolName,
					validatedArgs = resolved.validatedArgs,
					resolvedRequest = null,
					presentation = null
				).also {
					result = toolResult(
						content = resolved.errorMessage,
						presentation = resolved.presentation,
						status = ToolResultStatus.FAILURE,
					)
				}
				
				is ResolveResult.Activation -> toolCall(
					reason = resolved.reason,
					validatedToolName = ACTIVE_TOOL_NAME,
					validatedArgs = resolved.validatedArgs,
					resolvedRequest = null,
					presentation = null,
				).also {
					result = toolResult(
						content = resolved.message,
						presentation = resolved.presentation,
						status = ToolResultStatus.SUCCESS
					)
				}
				
				is ResolveResult.NeedsApproval -> toolCall(
					reason = resolved.reason,
					validatedToolName = resolved.toolName,
					validatedArgs = resolved.validatedArgs,
					resolvedRequest = resolved.resolveResult.result,
					presentation = resolved.resolveResult.request(resolved.reason),
				).also {
					ready = resolved.resolveResult
				}
			}
			
			AgentToolCallImpl(call, ready, result)
		}
	)
}
