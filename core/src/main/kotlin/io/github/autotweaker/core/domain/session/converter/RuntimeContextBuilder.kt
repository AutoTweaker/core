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

import io.github.autotweaker.api.orNow
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.RuntimeContext
import kotlinx.serialization.json.JsonNull
import java.util.*

class RuntimeContextBuilder(
	private val context: AgentContext,
	private val messages: Map<UUID, AgentMessage>
) {
	operator fun invoke(): RuntimeContext = context.let {
		RuntimeContext(
			systemPrompt = it.systemPrompt,
			injections = it.injections,
			compactedRounds = it.index.compactedRounds?.transform(),
			historyRounds = it.index.historyRounds?.map { round -> round.transform() },
			currentRound = it.index.currentRound?.transform()
		)
	}
	
	private fun AgentContextIndex.CompactedRounds.transform(): RuntimeContext.CompactedRounds =
		RuntimeContext.CompactedRounds(
			compactedRounds = compactedRounds?.transform(),
			rounds = rounds.map { it.transform() },
			summarizedMessage = summarizedMessage(summarizedMessage)
		)
	
	private fun AgentContextIndex.CompletedRound.transform() = RuntimeContext.CompletedRound(
		userMessage = userMessage(userMessage),
		turns = turns?.map { it.transform() },
		finalAssistantMessage = finalAssistantMessage?.let { assistantMessage(it) }
	)
	
	private fun AgentContextIndex.CurrentRound.transform() = RuntimeContext.CurrentRound(
		userMessage = userMessage(userMessage),
		turns = turns?.map { it.transform() },
		assistantMessage = assistantMessage?.let { assistantMessage(it) },
		pendingToolCalls = pendingToolCalls?.map { pendingToolCall(it) }
	)
	
	private fun AgentContextIndex.Turn.transform() = RuntimeContext.Turn(
		assistantMessage = assistantMessage(assistantMessage),
		tools = tools.map { it.transform() }
	)
	
	private fun AgentContextIndex.Turn.Tool.transform() = message<AgentMessage.Tool.Call>(call).let {
		RuntimeContext.Message.Tool(
			name = it?.name.orEmpty(),
			call = toolCall(call),
			callId = it?.callId.orEmpty(),
			result = toolResult(result)
		)
	}
	
	private fun userMessage(id: UUID) = message<AgentMessage.User>(id).let {
		RuntimeContext.Message.User(
			id = id,
			content = it?.content ?: MessageContent(),
			timestamp = it?.timestamp.orNow()
		)
	}
	
	private fun assistantMessage(id: UUID) = message<AgentMessage.Assistant>(id).let {
		RuntimeContext.Message.Assistant(
			id = id,
			reasoning = it?.reasoning,
			content = it?.content,
			modelId = it?.model ?: UUID.randomUUID(),
			timestamp = it?.timestamp.orNow(),
			usageSnapshot = it?.usageSnapshot
		)
	}
	
	private fun pendingToolCall(id: UUID) = message<AgentMessage.Tool.Call>(id).let {
		RuntimeContext.CurrentRound.PendingToolCall(
			id = id,
			callId = it?.callId.orEmpty(),
			name = it?.name.orEmpty(),
			arguments = it?.arguments.orEmpty(),
			reason = it?.reason.orEmpty(),
			timestamp = it?.timestamp.orNow(),
			validatedArgs = it?.validatedArgs ?: JsonNull
		)
	}
	
	private fun toolCall(id: UUID) = message<AgentMessage.Tool.Call>(id).let {
		RuntimeContext.Message.Tool.Call(
			id = id,
			arguments = it?.arguments.orEmpty(),
			reason = it?.reason,
			timestamp = it?.timestamp.orNow(),
			validatedArgs = it?.validatedArgs
		)
	}
	
	private fun toolResult(id: UUID) = message<AgentMessage.Tool.Result>(id).let {
		RuntimeContext.Message.Tool.Result(
			id = id,
			content = it?.content.orEmpty(),
			timestamp = it?.timestamp.orNow(),
			status = it?.status ?: ToolResultStatus.FAILURE
		)
	}
	
	private fun summarizedMessage(id: UUID) = message<AgentMessage.Compact>(id).let {
		RuntimeContext.SummarizedMessage(
			id = id,
			timestamp = it?.timestamp.orNow(),
			content = it?.content.orEmpty(),
			snapshots = it?.snapshots
		)
	}
	
	private inline fun <reified T : AgentMessage> message(id: UUID): T? = messages[id] as? T
}
