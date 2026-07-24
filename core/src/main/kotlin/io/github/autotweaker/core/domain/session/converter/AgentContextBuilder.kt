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

package io.github.autotweaker.core.domain.session.converter

import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.core.domain.agent.RuntimeContext
import java.util.*

class AgentContextBuilder(
	private val old: AgentContext,
	private val new: RuntimeContext,
) {
	private val messages = mutableMapOf<UUID, AgentMessage>()
	
	operator fun invoke(): Pair<AgentContext, List<AgentMessage>> = new.transform().let {
		AgentContext(
			systemPrompt = new.systemPrompt ?: old.systemPrompt,
			injections = new.injections,
			index = it,
			droppedMessages = old.index.ids() - it.ids()
		) to messages.values.toList()
	}
	
	private fun RuntimeContext.transform() = AgentContextIndex(
		compactedRounds = compactedRounds?.transform(),
		historyRounds = historyRounds?.map { it.transform() },
		currentRound = currentRound?.transform()
	)
	
	private fun RuntimeContext.CompactedRounds.transform(): AgentContextIndex.CompactedRounds =
		AgentContextIndex.CompactedRounds(
			compactedRounds = compactedRounds?.transform(),
			rounds = rounds.map { it.transform() },
			summarizedMessage = summarizedMessage.id()
		)
	
	private fun RuntimeContext.CompletedRound.transform() = AgentContextIndex.CompletedRound(
		userMessage = userMessage.id(),
		turns = turns?.map { it.transform() },
		finalAssistantMessage = finalAssistantMessage?.id()
	)
	
	private fun RuntimeContext.CurrentRound.transform() = AgentContextIndex.CurrentRound(
		userMessage = userMessage.id(),
		turns = turns?.map { it.transform() },
		assistantMessage = assistantMessage?.id(),
		pendingToolCalls = pendingToolCalls?.map { it.id() }
	)
	
	private fun RuntimeContext.Turn.transform() = AgentContextIndex.Turn(
		assistantMessage = assistantMessage.id(),
		tools = tools.map { it.transform() }
	)
	
	private fun RuntimeContext.Message.Tool.transform() = AgentContextIndex.Turn.Tool(
		call = call(),
		result = result()
	)
	
	private fun RuntimeContext.Message.User.id(): UUID =
		AgentMessage.User(
			id = id,
			timestamp = timestamp,
			content = content
		).add()
	
	private fun RuntimeContext.Message.Assistant.id(): UUID =
		AgentMessage.Assistant(
			id = id,
			timestamp = timestamp,
			reasoning = reasoning,
			content = content,
			model = modelId,
			usageSnapshot = usageSnapshot
		).add()
	
	private fun RuntimeContext.SummarizedMessage.id(): UUID =
		AgentMessage.Compact(
			id = id,
			timestamp = timestamp,
			content = content,
			snapshots = snapshots
		).add()
	
	private fun RuntimeContext.Message.Tool.call(): UUID =
		AgentMessage.Tool.Call(
			id = call.id,
			timestamp = call.timestamp,
			callId = callId,
			callName = call.callName,
			arguments = call.arguments,
			reason = call.reason,
			validatedToolName = call.validatedToolName,
			validatedArgs = call.validatedArgs
		).add()
	
	private fun RuntimeContext.Message.Tool.result(): UUID =
		AgentMessage.Tool.Result(
			id = result.id,
			timestamp = result.timestamp,
			callId = callId,
			content = result.content,
			status = result.status,
		).add()
	
	private fun RuntimeContext.CurrentRound.PendingToolCall.id(): UUID =
		AgentMessage.Tool.Call(
			id = id,
			timestamp = timestamp,
			callId = callId,
			callName = callName,
			arguments = arguments,
			reason = reason,
			validatedToolName = validatedToolName,
			validatedArgs = validatedArgs
		).add()
	
	private fun AgentMessage.add(): UUID = id.also { messages[id] = this }
}
