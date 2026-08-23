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

import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.format
import io.github.autotweaker.api.get
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.ResolveResult
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Instant
import io.github.autotweaker.api.types.llm.ChatMessage.Assistant.ToolCall as RawToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.CurrentRound.PendingToolCall as PendingCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool as ToolMessage
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Call as ToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Result as ToolResult

object ToolMessageFactory {
	fun buildPending(
		timestamp: Instant,
		call: RawToolCall,
		resolved: ResolveResult.NeedsApproval
	) = RuntimeContext.CurrentRound.PendingToolCall(
		id = UUID(),
		timestamp = timestamp,
		callId = call.id,
		callName = call.name,
		arguments = call.arguments,
		reason = resolved.reason,
		validatedToolName = resolved.toolName,
		validatedArgs = resolved.validatedArgs,
		resolvedRequest = resolved.resolveResult.result,
		presentation = resolved.resolveResult.request(resolved.reason),
	)
	
	//错误/激活
	
	fun buildImmediateResults(
		result: ThinkingStage.Result
	): List<ToolMessage> = buildList {
		result.parseFailures?.forEach {
			add(
				buildParseError(
					timestamp = result.assistantMessage.timestamp,
					call = it.first,
					message = it.second.errorMessage,
					presentation = it.second.presentation
				)
			)
		}
		result.resolveFailures?.forEach {
			add(
				buildResolveError(
					timestamp = result.assistantMessage.timestamp,
					call = it.first,
					reason = it.second.reason,
					validatedToolName = it.second.toolName,
					validatedArgs = it.second.validatedArgs,
					message = it.second.errorMessage,
					presentation = it.second.presentation
				)
			)
		}
		result.activations?.forEach {
			add(
				buildActivation(
					timestamp = result.assistantMessage.timestamp,
					activation = it
				)
			)
		}
	}
	
	//拒绝/错误/激活
	
	fun buildRejected(
		call: PendingCall,
		reason: String?,
		presentation: ToolPresentation
	) = buildToolMessage(
		call, ToolResult(
			id = UUID(),
			content = if (reason != null) ToolSettings.RejectedWithFeedback().format(reason)
			else ToolSettings.Rejected().get(),
			data = null,
			presentation = presentation,
			timestamp = Clock.System.now(),
			status = ToolResultStatus.REJECTED,
		)
	)
	
	fun buildCancelled(
		call: PendingCall,
		message: String,
		presentation: ToolPresentation
	) = buildToolMessage(
		call = call,
		ToolResult(
			id = UUID(),
			content = message,
			data = null,
			presentation = presentation,
			timestamp = Clock.System.now(),
			status = ToolResultStatus.CANCELLED
		)
	)
	
	fun buildParseError(
		timestamp: Instant,
		call: RawToolCall,
		message: String,
		presentation: ToolPresentation
	) = buildToolMessage(
		timestamp, call,
		ToolResult(
			id = UUID(),
			content = message,
			data = null,
			presentation = presentation,
			timestamp = timestamp,
			status = ToolResultStatus.FAILURE,
		)
	)
	
	fun buildResolveError(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
		message: String,
		presentation: ToolPresentation
	) = buildToolMessage(
		timestamp, call, reason, validatedToolName, validatedArgs,
		ToolResult(
			id = UUID(),
			content = message,
			data = null,
			presentation = presentation,
			timestamp = timestamp,
			status = ToolResultStatus.FAILURE,
		)
	)
	
	fun buildActivation(
		timestamp: Instant,
		activation: Pair<RawToolCall, ResolveResult.Activation>,
	) = buildToolMessage(
		timestamp, activation.first,
		ToolResult(
			id = UUID(),
			content = activation.second.message,
			data = null,
			presentation = activation.second.presentation,
			timestamp = timestamp,
			status = ToolResultStatus.SUCCESS,
		),
	)
	
	//buildToolMessage
	
	// 完整的
	fun buildToolMessage(
		call: PendingCall,
		result: ToolResult,
	) = ToolMessage(
		callId = call.callId,
		call = buildToolCall(call),
		result = result,
	)
	
	// 没有解析出 ToolArgs 的
	fun buildToolMessage(
		timestamp: Instant,
		call: RawToolCall,
		result: ToolResult,
	) = ToolMessage(
		callId = call.id,
		call = buildToolCall(timestamp, call),
		result = result,
	)
	
	// resolve 失败的
	fun buildToolMessage(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
		result: ToolResult,
	) = ToolMessage(
		callId = call.id,
		call = buildToolCall(
			timestamp, call, reason, validatedToolName, validatedArgs
		),
		result = result,
	)
	
	//buildToolCall
	
	fun buildToolCall(
		call: PendingCall,
	) = ToolCall( // 完全解析成功的
		id = UUID(),
		timestamp = call.timestamp,
		callName = call.callName,
		arguments = call.arguments,
		reason = call.reason,
		validatedToolName = call.validatedToolName,
		validatedArgs = call.validatedArgs,
		resolvedRequest = call.resolvedRequest,
		presentation = call.presentation
	)
	
	fun buildToolCall(
		timestamp: Instant,
		call: RawToolCall,
	) = ToolCall( // 没有解析出 ToolArgs 的
		id = UUID(),
		timestamp = timestamp,
		callName = call.name,
		arguments = call.arguments,
		reason = null,
		validatedToolName = null,
		validatedArgs = null,
		resolvedRequest = null,
		presentation = null // call的presentation是审批用的，解析失败自然没法审批
	)
	
	fun buildToolCall(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
	) = ToolCall( // resolve 失败的
		id = UUID(),
		timestamp = timestamp,
		callName = call.name,
		arguments = call.arguments,
		reason = reason,
		validatedToolName = validatedToolName,
		validatedArgs = validatedArgs,
		resolvedRequest = null,
		presentation = null // call的presentation是审批用的，解析失败自然没法审批
	)
}
