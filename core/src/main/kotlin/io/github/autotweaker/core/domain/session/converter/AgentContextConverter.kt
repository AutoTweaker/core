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

object AgentContextConverter {
	fun sync(ctx: AgentContext, oldCtx: SessionContext): Result {
		val newMessages = extractMessages(ctx)
		val newMessageIds = newMessages.map { it.id }.toSet()
		
		val oldNonCompactedIds = collectNonCompactedIds(oldCtx)
		val dropped = ((oldNonCompactedIds - newMessageIds) + (oldCtx.droppedMessages ?: emptyList()))
			.toList().takeIf { it.isNotEmpty() }
		
		return Result(
			messages = newMessages,
			index = buildIndex(ctx, oldCtx),
			droppedMessageIds = dropped
		)
	}
	
	data class Result(
		val messages: List<SessionMessage>,
		val index: SessionContextIndex,
		val droppedMessageIds: List<UUID>?,
	)
	
	// region Message extraction
	
	private fun extractMessages(ctx: AgentContext): List<SessionMessage> {
		val messages = mutableListOf<SessionMessage>()
		
		ctx.compactedRounds?.forEach { compacted ->
			messages.add(
				SessionMessage.Compact(
					id = compacted.summarizedMessage.id,
					timestamp = compacted.summarizedMessage.timestamp,
					content = compacted.summarizedMessage.content,
					snapshots = compacted.summarizedMessage.snapshots
				)
			)
			compacted.rounds.forEach { round ->
				messages.addAll(extractCompletedRound(round))
			}
		}
		
		ctx.historyRounds?.forEach { round ->
			messages.addAll(extractCompletedRound(round))
		}
		
		ctx.currentRound?.let { round ->
			messages.addAll(extractRoundMessages(round.userMessage, round.turns, round.assistantMessage))
			round.pendingToolCalls?.forEach { pending ->
				messages.add(
					SessionMessage.Tool.Call(
						id = pending.id,
						timestamp = pending.timestamp,
						callId = pending.callId,
						assistantMessage = pending.assistantMessageId,
						name = pending.name,
						arguments = pending.arguments,
						reason = pending.reason,
						validatedArgs = pending.validatedArgs,
					)
				)
			}
		}
		
		return messages
	}
	
	private fun extractCompletedRound(round: AgentContext.CompletedRound): List<SessionMessage> {
		return extractRoundMessages(round.userMessage, round.turns, round.finalAssistantMessage)
	}
	
	private fun extractRoundMessages(
		userMessage: AgentContext.Message.User,
		turns: List<AgentContext.Turn>?,
		assistantMessage: AgentContext.Message.Assistant?,
	): List<SessionMessage> {
		val messages = mutableListOf<SessionMessage>()
		messages.add(convertUser(userMessage))
		turns?.forEach { turn ->
			messages.add(convertAssistant(turn.assistantMessage))
			turn.tools.forEach { tool ->
				messages.add(convertToolCall(tool))
				messages.add(convertToolResult(tool))
			}
		}
		assistantMessage?.let { messages.add(convertAssistant(it)) }
		return messages
	}
	
	// endregion
	
	// region Index building
	
	private fun buildIndex(ctx: AgentContext, oldCtx: SessionContext): SessionContextIndex {
		val newCompactedIds = ctx.compactedRounds?.map { it.summarizedMessage.id }?.toSet().orEmpty()
		val preservedCompacted = oldCtx.index.compactedRounds?.filter { it.summarizedMessage !in newCompactedIds }
		val mergedCompacted = (ctx.compactedRounds?.map { compacted ->
			SessionContextIndex.CompactedRound(
				summarizedMessage = compacted.summarizedMessage.id,
				rounds = compacted.rounds.map { toCompletedRound(it) }
			)
		}.orEmpty() + preservedCompacted.orEmpty()).takeIf { it.isNotEmpty() }
		
		return SessionContextIndex(
			compactedRounds = mergedCompacted,
			historyRounds = ctx.historyRounds?.map { toCompletedRound(it) },
			currentRound = ctx.currentRound?.let { round ->
				SessionContextIndex.CurrentRound(
					userMessage = round.userMessage.id,
					turns = round.turns?.map(::toTurn),
					assistantMessage = round.assistantMessage?.id,
					pendingToolCalls = round.pendingToolCalls?.map { it.id }
				)
			},
			summarizedMessage = ctx.summarizedMessage?.id
		)
	}
	
	private fun toCompletedRound(
		completed: AgentContext.CompletedRound
	): SessionContextIndex.CompactedRound.CompletedRound {
		return SessionContextIndex.CompactedRound.CompletedRound(
			userMessage = completed.userMessage.id,
			turns = completed.turns?.map(::toTurn),
			finalAssistantMessage = completed.finalAssistantMessage?.id
		)
	}
	
	private fun toTurn(turn: AgentContext.Turn): SessionContextIndex.Turn {
		return SessionContextIndex.Turn(
			assistantMessage = turn.assistantMessage.id,
			tools = turn.tools.map { tool ->
				SessionContextIndex.Turn.Tool(
					call = tool.call.id,
					result = tool.result.id
				)
			}
		)
	}
	
	// endregion
	
	// region Message conversion
	
	private fun convertUser(msg: AgentContext.Message.User): SessionMessage.User {
		return SessionMessage.User(
			id = msg.id,
			timestamp = msg.timestamp,
			content = msg.content,
			images = msg.images
		)
	}
	
	private fun convertAssistant(msg: AgentContext.Message.Assistant): SessionMessage.Assistant {
		return SessionMessage.Assistant(
			id = msg.id,
			timestamp = msg.timestamp,
			reasoning = msg.reasoning,
			content = msg.content,
			model = msg.modelId,
			usageSnapshot = msg.usageSnapshot
		)
	}
	
	private fun convertToolCall(tool: AgentContext.Message.Tool): SessionMessage.Tool.Call {
		return SessionMessage.Tool.Call(
			id = tool.call.id,
			timestamp = tool.call.timestamp,
			callId = tool.callId,
			assistantMessage = tool.call.assistantMessageId,
			name = tool.name,
			arguments = tool.call.arguments,
			reason = tool.call.reason,
			validatedArgs = tool.call.validatedArgs,
		)
	}
	
	private fun convertToolResult(tool: AgentContext.Message.Tool): SessionMessage.Tool.Result {
		return SessionMessage.Tool.Result(
			id = tool.result.id,
			timestamp = tool.result.timestamp,
			callId = tool.callId,
			content = tool.result.content,
			status = tool.result.status
		)
	}
	
	// endregion
	
	// region Dropped tracking
	
	private fun collectNonCompactedIds(ctx: SessionContext): Set<UUID> {
		val ids = mutableSetOf<UUID>()
		val index = ctx.index
		
		index.historyRounds?.forEach { SessionContextIndex.collectCompletedRoundIds(it, ids) }
		index.currentRound?.let { SessionContextIndex.collectCurrentRoundIds(it, ids) }
		index.summarizedMessage?.let { ids.add(it) }
		
		return ids
	}
	
	// endregion
}
