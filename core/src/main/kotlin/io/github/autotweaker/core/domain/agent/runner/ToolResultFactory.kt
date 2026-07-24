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

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.ToolActivation
import io.github.autotweaker.core.domain.agent.think.ThinkingStage
import io.github.autotweaker.core.domain.agent.tool.AgentToolSettings
import kotlin.time.Clock
import kotlin.time.Instant
import io.github.autotweaker.api.types.llm.ChatMessage.AssistantMessage.ToolCall as RawToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.CurrentRound.PendingToolCall as PendingCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool as ToolMessage
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Call as ToolCall
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool.Result as ToolResult

class ToolResultFactory : Settable {
	
	//错误/激活
	
	fun buildImmediateResults(
		timestamp: Instant,
		activations: List<ToolActivation>,
		parseFailures: List<ThinkingStage.ParseFailure>,
	): List<ToolMessage> = buildList {
		parseFailures.forEach { add(buildError(it.toolCall, timestamp, it.errorMessage)) }
		activations.forEach { add(buildActivation(timestamp, it)) }
	}
	
	//拒绝/错误/激活
	
	fun buildRejected(
		call: PendingCall,
		reason: String?,
	) = buildToolMessage(
		call, ToolResult(
			content = if (reason != null) setting(AgentToolSettings.RejectedWithFeedback()).format(reason) else
				setting(AgentToolSettings.Rejected()),
			timestamp = Clock.System.now(),
			status = ToolResultStatus.REJECTED,
		)
	)
	
	fun buildError(
		call: RawToolCall,
		timestamp: Instant,
		message: String,
	) = buildToolMessage(
		call, timestamp,
		ToolResult(
			content = message,
			timestamp = Clock.System.now(),
			status = ToolResultStatus.FAILURE,
		)
	)
	
	fun buildActivation(
		timestamp: Instant,
		activation: ToolActivation,
	) = buildToolMessage(
		activation.toolCall, timestamp,
		ToolResult(
			content = activation.message,
			timestamp = Clock.System.now(),
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
		call: RawToolCall,
		timestamp: Instant,
		result: ToolResult,
	) = ToolMessage(
		callId = call.id,
		call = buildToolCall(call, timestamp),
		result = result,
	)
	
	//buildToolCall
	
	fun buildToolCall(
		call: PendingCall,
	) = ToolCall(
		callName = call.callName,
		arguments = call.arguments,
		reason = call.reason,
		timestamp = call.timestamp,
		validatedToolName = call.validatedToolName,
		validatedArgs = call.validatedArgs,
	)
	
	fun buildToolCall(
		call: RawToolCall,
		timestamp: Instant,
	) = ToolCall(
		callName = call.name,
		arguments = call.arguments,
		timestamp = timestamp,
	)
}
