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

import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.domain.agent.AgentContext
import java.util.*

object SessionContextConverter {
	fun buildAgentContext(
		context: SessionContext,
		messages: List<SessionMessage>,
		maxCompactedRounds: Int = 0
	): AgentContext {
		val messageMap = messages.associateBy { it.id }
		
		val compactedRounds = if (maxCompactedRounds <= 0) null
		else context.index.compactedRounds?.takeLast(maxCompactedRounds)?.mapNotNull { compacted ->
			if (compacted.rounds.isEmpty()) return@mapNotNull null
			val compactMsg =
				messageMap[compacted.summarizedMessage] as? SessionMessage.Compact ?: return@mapNotNull null
			val rounds = compacted.rounds.map { buildCompletedRound(it, messageMap) }
			if (rounds.any { it == null }) return@mapNotNull null
			AgentContext.CompactedRound(
				rounds = rounds.filterNotNull(), summarizedMessage = AgentContext.SummarizedMessage(
					id = compactMsg.id,
					timestamp = compactMsg.timestamp,
					content = compactMsg.content,
					snapshots = compactMsg.snapshots
				)
			)
		}
		
		val historyRounds = context.index.historyRounds?.mapNotNull {
			buildCompletedRound(it, messageMap)
		}
		
		val summarizedMessage =
			context.index.summarizedMessage?.let { messageMap[it] as? SessionMessage.Compact }?.let {
				AgentContext.SummarizedMessage(
					id = it.id, timestamp = it.timestamp, content = it.content, snapshots = it.snapshots
				)
			}
		
		return AgentContext(
			compactedRounds = compactedRounds,
			systemPrompt = context.systemPrompt.takeIf { it.isNotEmpty() },
			injections = context.injections,
			historyRounds = historyRounds,
			summarizedMessage = summarizedMessage,
			currentRound = null
		)
	}
	
	private fun buildCompletedRound(
		roundIndex: SessionContextIndex.CompactedRound.CompletedRound,
		messageMap: Map<UUID, SessionMessage>
	): AgentContext.CompletedRound? {
		val userMsg = messageMap[roundIndex.userMessage] as? SessionMessage.User ?: return null
		
		val turns = roundIndex.turns?.mapNotNull { buildTurn(it, messageMap) }
		val finalAssistant = roundIndex.finalAssistantMessage?.let { messageMap[it] as? SessionMessage.Assistant }
			?.let { buildAssistantMessage(it) }
		
		return AgentContext.CompletedRound(
			userMessage = buildUserMessage(userMsg), turns = turns, finalAssistantMessage = finalAssistant
		)
	}
	
	private fun buildTurn(
		turnIndex: SessionContextIndex.Turn, messageMap: Map<UUID, SessionMessage>
	): AgentContext.Turn? {
		val assistantMsg = messageMap[turnIndex.assistantMessage] as? SessionMessage.Assistant ?: return null
		
		val tools = turnIndex.tools.mapNotNull { toolIndex ->
			val callMsg = messageMap[toolIndex.call] as? SessionMessage.Tool.Call ?: return@mapNotNull null
			val resultMsg = messageMap[toolIndex.result] as? SessionMessage.Tool.Result ?: return@mapNotNull null
			
			AgentContext.Message.Tool(
				name = callMsg.name, call = AgentContext.Message.Tool.Call(
					id = callMsg.id,
					assistantMessageId = callMsg.assistantMessage,
					arguments = callMsg.arguments,
					reason = callMsg.reason,
					timestamp = callMsg.timestamp,
					validatedArgs = callMsg.validatedArgs,
				), callId = callMsg.callId, result = AgentContext.Message.Tool.Result(
					id = resultMsg.id,
					content = resultMsg.content,
					timestamp = resultMsg.timestamp,
					status = resultMsg.status
				)
			)
		}
		
		return AgentContext.Turn(
			assistantMessage = buildAssistantMessage(assistantMsg), tools = tools
		)
	}
	
	private fun buildUserMessage(msg: SessionMessage.User): AgentContext.Message.User {
		return AgentContext.Message.User(
			id = msg.id, content = msg.content, timestamp = msg.timestamp
		)
	}
	
	private fun buildAssistantMessage(
		msg: SessionMessage.Assistant
	): AgentContext.Message.Assistant {
		return AgentContext.Message.Assistant(
			id = msg.id,
			reasoning = msg.reasoning,
			content = msg.content,
			modelId = msg.model,
			timestamp = msg.timestamp,
			usageSnapshot = msg.usageSnapshot
		)
	}
}
