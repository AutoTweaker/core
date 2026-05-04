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

package io.github.autotweaker.core.session

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.llm.Usage
import java.util.*

@Suppress("unused")
object SessionContextConverter {
	fun toAgentContext(
		context: SessionContext,
		messages: List<SessionMessage>,
		defaultModel: Model,
		resolveModel: (ModelId) -> Model
	): AgentContext {
		val messageMap = messages.associateBy { it.id }
		val safeResolveModel: (ModelId) -> Model = { modelId ->
			try {
				resolveModel(modelId)
			} catch (_: Exception) {
				defaultModel
			}
		}
		
		val compactedRounds = context.index.compactedRounds?.mapNotNull { compacted ->
			if (compacted.rounds.isEmpty()) return@mapNotNull null
			val compactMsg = messageMap[compacted.summarizedMessage] as? SessionMessage.Compact
				?: return@mapNotNull null
			val rounds = compacted.rounds.map { buildCompletedRound(it, messageMap, context.usage, safeResolveModel) }
			if (rounds.any { it == null }) return@mapNotNull null
			AgentContext.CompactedRound(
				compactedAt = compactMsg.timestamp,
				rounds = rounds.filterNotNull(),
				summarizedMessage = compactMsg.content
			)
		}
		
		val historyRounds = context.index.historyRounds?.mapNotNull {
			buildCompletedRound(it, messageMap, context.usage, safeResolveModel)
		}
		
		val currentRound = context.index.currentRound?.let {
			buildCurrentRound(it, messageMap, context.usage, safeResolveModel)
		}
		
		val summarizedMessage = context.index.summarizedMessage
			?.let { messageMap[it] as? SessionMessage.Compact }
			?.content
		
		return AgentContext(
			compactedRounds = compactedRounds,
			systemPrompt = context.systemPrompt.takeIf { it.isNotEmpty() },
			historyRounds = historyRounds,
			summarizedMessage = summarizedMessage,
			currentRound = currentRound
		)
	}
	
	private fun buildCompletedRound(
		roundIndex: SessionContextIndex.CompactedRound.CompletedRound,
		messageMap: Map<UUID, SessionMessage>,
		usage: Map<UUID, Usage>,
		resolveModel: (ModelId) -> Model
	): AgentContext.CompletedRound? {
		val userMsg = messageMap[roundIndex.userMessage] as? SessionMessage.User ?: return null
		
		val turns = roundIndex.turns?.mapNotNull { buildTurn(it, messageMap, usage, resolveModel) }
		val finalAssistant = roundIndex.finalAssistantMessage
			?.let { messageMap[it] as? SessionMessage.Assistant }
			?.let { buildAssistantMessage(it, usage, resolveModel) }
		
		return AgentContext.CompletedRound(
			userMessage = buildUserMessage(userMsg),
			turns = turns,
			finalAssistantMessage = finalAssistant
		)
	}
	
	private fun buildCurrentRound(
		roundIndex: SessionContextIndex.CurrentRound,
		messageMap: Map<UUID, SessionMessage>,
		usage: Map<UUID, Usage>,
		resolveModel: (ModelId) -> Model
	): AgentContext.CurrentRound? {
		val userMsg = messageMap[roundIndex.userMessage] as? SessionMessage.User ?: return null
		
		val turns = roundIndex.turns?.mapNotNull { buildTurn(it, messageMap, usage, resolveModel) }
		
		val assistantMsg = roundIndex.assistantMessage
			?.let { messageMap[it] as? SessionMessage.Assistant }
		
		val pendingToolCalls = roundIndex.pendingToolCalls?.mapNotNull { callId ->
			val call = messageMap[callId] as? SessionMessage.Tool.Call ?: return@mapNotNull null
			val assistantModel = (messageMap[call.assistantMessage] as? SessionMessage.Assistant)?.model
				?: return@mapNotNull null
			AgentContext.CurrentRound.PendingToolCall(
				callId = call.callId,
				assistantMessageId = call.assistantMessage,
				name = call.name,
				model = resolveModel(assistantModel),
				arguments = call.arguments,
				reason = call.reason,
				timestamp = call.timestamp
			)
		}
		
		return AgentContext.CurrentRound(
			userMessage = buildUserMessage(userMsg),
			turns = turns,
			assistantMessage = assistantMsg?.let { buildAssistantMessage(it, usage, resolveModel) },
			pendingToolCalls = pendingToolCalls?.takeIf { it.isNotEmpty() }
		)
	}
	
	private fun buildTurn(
		turnIndex: SessionContextIndex.Turn,
		messageMap: Map<UUID, SessionMessage>,
		usage: Map<UUID, Usage>,
		resolveModel: (ModelId) -> Model
	): AgentContext.Turn? {
		val assistantMsg = messageMap[turnIndex.assistantMessage] as? SessionMessage.Assistant
			?: return null
		
		val tools = turnIndex.tools.mapNotNull { toolIndex ->
			val callMsg = messageMap[toolIndex.call] as? SessionMessage.Tool.Call ?: return@mapNotNull null
			val resultMsg = messageMap[toolIndex.result] as? SessionMessage.Tool.Result ?: return@mapNotNull null
			
			AgentContext.Message.Tool(
				name = callMsg.name,
				call = AgentContext.Message.Tool.Call(
					id = callMsg.id,
					assistantMessageId = callMsg.assistantMessage,
					arguments = callMsg.arguments,
					reason = callMsg.reason,
					timestamp = callMsg.timestamp,
					model = resolveModel(assistantMsg.model)
				),
				callId = callMsg.callId,
				result = AgentContext.Message.Tool.Result(
					id = resultMsg.id,
					content = resultMsg.content,
					timestamp = resultMsg.timestamp,
					status = resultMsg.status
				)
			)
		}
		
		return AgentContext.Turn(
			assistantMessage = buildAssistantMessage(assistantMsg, usage, resolveModel),
			tools = tools
		)
	}
	
	private fun buildUserMessage(msg: SessionMessage.User): AgentContext.Message.User {
		return AgentContext.Message.User(
			id = msg.id,
			content = msg.content,
			images = msg.images,
			timestamp = msg.timestamp
		)
	}
	
	private fun buildAssistantMessage(
		msg: SessionMessage.Assistant,
		usage: Map<UUID, Usage>,
		resolveModel: (ModelId) -> Model
	): AgentContext.Message.Assistant {
		return AgentContext.Message.Assistant(
			id = msg.id,
			reasoning = msg.reasoning,
			content = msg.content,
			model = resolveModel(msg.model),
			timestamp = msg.timestamp,
			usage = usage[msg.id]
		)
	}
}
