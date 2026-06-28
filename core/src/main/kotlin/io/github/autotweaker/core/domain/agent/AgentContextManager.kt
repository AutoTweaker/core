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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.ReentrantMutex
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.tool.ToolResultStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import io.github.autotweaker.core.domain.agent.AgentContext.Message.Tool as ToolMessage

class AgentContextManager(initial: AgentContext, private val cancelledMessage: String) {
	private val _context = MutableStateFlow(initial)
	private val lock = ReentrantMutex()
	
	val context: StateFlow<AgentContext> = _context.asStateFlow()
	
	private val pendingToolResults = mutableListOf<ToolMessage>()
	
	suspend fun get(): AgentContext = lock.withLock { _context.value }
	
	suspend fun beginRound(userMessage: AgentContext.Message.User) = lock.withLock {
		check(_context.value.currentRound == null)
		check(pendingToolResults.isEmpty())
		val round = AgentContext.CurrentRound(userMessage = userMessage, turns = null)
		_context.update { it.copy(currentRound = round) }
	}
	
	suspend fun applyThinking(
		assistant: AgentContext.Message.Assistant,
		pendingCalls: List<AgentContext.CurrentRound.PendingToolCall>,
		immediateResults: List<ToolMessage>,
	) = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		check(current.assistantMessage == null)
		check(current.pendingToolCalls == null)
		check(pendingToolResults.isEmpty())
		pendingToolResults.addAll(immediateResults)
		_context.update {
			it.copy(
				currentRound = current.copy(assistantMessage = assistant, pendingToolCalls = pendingCalls)
			)
		}
	}
	
	suspend fun recordToolResult(tool: ToolMessage) = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		check(current.assistantMessage != null)
		check(current.pendingToolCalls != null)
		check(current.pendingToolCalls.any { it.callId == tool.callId })
		pendingToolResults.add(tool)
	}
	
	suspend fun finalizeToolTurn() = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		val assistant = requireNotNull(current.assistantMessage)
		val turn = AgentContext.Turn(assistantMessage = assistant, tools = pendingToolResults.toList())
		pendingToolResults.clear()
		_context.update {
			it.copy(
				currentRound = current.copy(
					turns = current.turns.orEmpty() + turn,
					assistantMessage = null,
					pendingToolCalls = null,
				)
			)
		}
	}
	
	suspend fun archiveCurrentRound() = lock.withLock {
		val round = _context.value.currentRound ?: return@withLock
		
		
		//丢弃空round
		if (round.assistantMessage == null && round.turns.isNullOrEmpty() &&
			round.pendingToolCalls.isNullOrEmpty()
		) {
			check(pendingToolResults.isEmpty())
			_context.update { it.copy(currentRound = null) }
			return@withLock
		}
		
		//生成CANCELLED消息
		if (round.pendingToolCalls != null) {
			val processedIds = pendingToolResults.map { it.callId }.toSet()
			round.pendingToolCalls.filter { it.callId !in processedIds }.forEach { call ->
				pendingToolResults.add(
					ToolMessage(
						name = call.name,
						callId = call.callId,
						call = ToolMessage.Call(
							assistantMessageId = requireNotNull(round.assistantMessage?.id),
							arguments = call.arguments,
							reason = call.reason,
							timestamp = call.timestamp,
							validatedArgs = call.validatedArgs,
						),
						result = ToolMessage.Result(
							content = cancelledMessage,
							timestamp = Clock.System.now(),
							status = ToolResultStatus.CANCELLED,
						),
					)
				)
			}
		}
		
		val assistantMsg = round.assistantMessage
		val archivedTurn =
			if (assistantMsg != null && pendingToolResults.isNotEmpty()) {
				AgentContext.Turn(assistantMsg, pendingToolResults.toList())
			} else null
		pendingToolResults.clear()
		
		val allTurns = buildList {
			round.turns?.let { addAll(it) }
			archivedTurn?.let { add(it) }
		}.ifEmpty { null }
		
		val completed = AgentContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = if (archivedTurn != null) null else assistantMsg,
		)
		_context.update {
			it.copy(
				currentRound = null,
				historyRounds = it.historyRounds.orEmpty() + completed,
			)
		}
	}
	
	suspend fun applyCompact(
		summarizedMessage: AgentContext.SummarizedMessage,
		rounds: List<AgentContext.CompletedRound>,
	) = lock.withLock {
		val currentHistory = requireNotNull(_context.value.historyRounds)
		check(rounds.all { it in currentHistory })
		val remaining = currentHistory.filter { it !in rounds }
		val compacted = AgentContext.CompactedRound(rounds = rounds, summarizedMessage = summarizedMessage)
		_context.update {
			it.copy(
				compactedRounds = it.compactedRounds.orEmpty() + compacted,
				historyRounds = remaining.ifEmpty { null },
			)
		}
	}
	
	suspend fun updateInjections(injections: List<ContextInjection>?) = lock.withLock {
		_context.update {
			it.copy(
				injections = injections
			)
		}
	}
}
