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

import io.github.autotweaker.api.get
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant
import io.github.autotweaker.api.types.llm.ChatMessage.AssistantMessage.ToolCall as RawToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.CurrentRound.PendingToolCall as PendingCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool as ToolMessage
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Call as ToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Result as ToolResult

object ToolResultFactory {
	
	//错误/激活
	
	fun buildImmediateResults(
		timestamp: Instant,
		activations: List<ToolActivation>,
		parseFailures: List<ThinkingStage.ParseFailure>,
		resolveFailures: List<ThinkingStage.ResolveFailure>
	): List<ToolMessage> = buildList {
		parseFailures.forEach { add(buildError(timestamp, it.toolCall, it.errorMessage)) }
		resolveFailures.forEach {
			add(
				buildError(
					timestamp = timestamp,
					call = it.toolCall,
					reason = it.reason,
					validatedToolName = it.validatedToolName,
					validatedArgs = it.validatedArgs,
					message = it.errorMessage
				)
			)
		}
		activations.forEach { add(buildActivation(timestamp, it)) }
	}
	
	//拒绝/错误/激活
	
	fun buildRejected(
		call: PendingCall,
		reason: String?,
	) = buildToolMessage(
		call, ToolResult(
			id = UUID.randomUUID(),
			content = if (reason != null) AgentToolSettings.RejectedWithFeedback().get().format(reason)
			else AgentToolSettings.Rejected().get(),
			data = null,
			timestamp = Clock.System.now(),
			status = ToolResultStatus.REJECTED,
		)
	)
	
	fun buildError(
		timestamp: Instant,
		call: RawToolCall,
		message: String,
	) = buildToolMessage(
		timestamp, call,
		ToolResult(
			id = UUID.randomUUID(),
			content = message,
			data = null,
			timestamp = timestamp,
			status = ToolResultStatus.FAILURE,
		)
	)
	
	fun buildError(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
		message: String,
	) = buildToolMessage(
		timestamp, call, reason, validatedToolName, validatedArgs,
		ToolResult(
			id = UUID.randomUUID(),
			content = message,
			data = null,
			timestamp = timestamp,
			status = ToolResultStatus.FAILURE,
		)
	)
	
	fun buildActivation(
		timestamp: Instant,
		activation: ToolActivation,
	) = buildToolMessage(
		timestamp, activation.toolCall,
		ToolResult(
			id = UUID.randomUUID(),
			content = activation.message,
			data = null,
			timestamp = timestamp,
			status = ToolResultStatus.SUCCESS,
		)
	)
	
	//buildToolMessage
	
	fun buildToolMessage(
		call: PendingCall,
		result: ToolResult,
	) = ToolMessage(
		callId = call.callId,
		call = buildToolCall(call),
		result = result,
	)
	
	fun buildToolMessage(
		timestamp: Instant,
		call: RawToolCall,
		result: ToolResult,
	) = ToolMessage(
		callId = call.id,
		call = buildToolCall(timestamp, call),
		result = result,
	)
	
	fun buildToolMessage(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
		result: ToolResult,
	) = ToolMessage(
		callId = call.id,
		call = buildToolCall(timestamp, call, reason, validatedToolName, validatedArgs),
		result = result,
	)
	
	//buildToolCall
	
	fun buildToolCall(
		call: PendingCall,
	) = ToolCall(
		id = UUID.randomUUID(),
		timestamp = call.timestamp,
		callName = call.callName,
		arguments = call.arguments,
		reason = call.reason,
		validatedToolName = call.validatedToolName,
		validatedArgs = call.validatedArgs,
		resolvedRequest = call.resolvedRequest
	)
	
	fun buildToolCall(
		timestamp: Instant,
		call: RawToolCall,
	) = ToolCall(
		id = UUID.randomUUID(),
		timestamp = timestamp,
		callName = call.name,
		arguments = call.arguments,
		reason = null,
		validatedToolName = null,
		validatedArgs = null,
		resolvedRequest = null
	)
	
	fun buildToolCall(
		timestamp: Instant,
		call: RawToolCall,
		reason: String,
		validatedToolName: String,
		validatedArgs: JsonElement,
	) = ToolCall(
		id = UUID.randomUUID(),
		timestamp = timestamp,
		callName = call.name,
		arguments = call.arguments,
		reason = reason,
		validatedToolName = validatedToolName,
		validatedArgs = validatedArgs,
		resolvedRequest = null
	)
}
