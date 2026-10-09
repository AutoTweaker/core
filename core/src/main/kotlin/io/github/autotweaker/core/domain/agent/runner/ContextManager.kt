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

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.get
import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.ToolCallStatus
import io.github.autotweaker.api.types.message.*
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.domain.agent.AgentToolCallImpl
import io.github.autotweaker.core.domain.agent.MessageBuilder
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

class ContextManager(
	initial: RuntimeContext, private val msg: MessageBuilder
) : I18nable {
	private val lock = ReentrantMutex()
	
	@Volatile
	private var _toolCalls: Pair<UUID, List<AgentToolCallImpl>>? = null
	val toolCalls get() = _toolCalls
	
	private val _context = MutableStateFlow(initial)
	val context: StateFlow<RuntimeContext> = _context.asStateFlow()
	
	private val cancelledPending = ToolSettings.CancelledPending().get()
	
	suspend fun get(): RuntimeContext = lock.withLock { _context.value }
	
	suspend fun beginRound(userRef: UserRef) = lock.withLock {
		check(_context.value.currentRound == null) { "Current round must be null to begin a new round" }
		_context.update {
			it.copy(
				currentRound = AgentContextIndex.Round(
					userMsgRef = userRef,
					turns = null,
					assistantMsgRef = null
				)
			)
		}
	}
	
	suspend fun applyThinking(
		assistantRef: AssistantRef,
		toolCalls: List<AgentToolCallImpl>?,
	) = lock.withLock {
		require(toolCalls == null || toolCalls.isNotEmpty())
		val current = checkNotNull(_context.value.currentRound) { "No current round to apply thinking" }
		check(current.assistantMessage == null) { "Assistant message already set" }
		check(_toolCalls == null) { "Tool calls already set" }
		
		_context.update {
			it.copy(
				currentRound = current.copy(
					assistantMsgRef = assistantRef,
				)
			)
		}
		toolCalls?.let { _toolCalls = assistantRef.id to it }
	}
	
	suspend fun finalizeToolTurn() = lock.withLock {
		val current = checkNotNull(_context.value.currentRound) { "No current round to finalize tool turn" }
		checkNotNull(current.assistantMsgRef) { "No assistant message to finalize tool turn" }
		val toolCalls = checkNotNull(_toolCalls) { "No tool calls to finalize tool turn" }
		check(toolCalls.second.all { it.status.value == ToolCallStatus.FINISHED }) { "Not all tool calls have finished" }
		val turn = AgentContextIndex.Turn(
			assistantMsgRef = AssistantRef(toolCalls.first),
			tools = toolCalls.second.map {
				AgentContextIndex.Turn.Tool(
					it.call.ref(), it.result!!.ref()
				)
			})
		
		_context.update {
			it.copy(
				currentRound = current.copy(
					turns = current.turns.orEmpty() + turn,
					assistantMsgRef = null
				)
			)
		}
		_toolCalls = null
	}
	
	suspend fun archiveCurrentRound() = lock.withLock {
		val round = _context.value.currentRound ?: return@withLock
		
		//丢弃空round
		if (round.assistantMessage == null && round.turns.isNullOrEmpty()) {
			check(_toolCalls == null) { "Tool calls must be null for an empty round" }
			_context.update { it.copy(currentRound = null) }
			return@withLock
		}
		
		cancelPending()
		
		val turns = round.turns.orEmpty().toMutableList()
		var assistantMessage = round.assistantMsgRef
		
		_toolCalls?.let { toolCalls ->
			check(assistantMessage?.id == toolCalls.first) { "Assistant message does not match tool calls" }
			turns.add(
				AgentContextIndex.Turn(
					AssistantRef(toolCalls.first),
					toolCalls.second.map {
						AgentContextIndex.Turn.Tool(
							it.call.ref(), it.result!!.ref()
						)
					}
				)
			)
			assistantMessage = null
		}
		
		_context.update {
			it.copy(
				currentRound = null,
				historyRounds = it.historyRounds.orEmpty() + round.copy(
					turns = turns.orNull(),
					assistantMsgRef = assistantMessage
				),
			)
		}
		_toolCalls = null
	}
	
	suspend fun cancelPending() = lock.withLock {
		_toolCalls?.second?.forEach {
			if (it.status.value != ToolCallStatus.FINISHED) it.finish(
				msg.toolResult(
					callId = it.call.callId,
					content = cancelledPending,
					data = null,
					presentation = it.resolved!!.cancelled(),
					status = ToolResultStatus.CANCELLED
				)
			)
		}
	}
	
	suspend fun applyCompact(
		summary: CompactRef,
		rounds: List<AgentContextIndex.Round>,
	) = lock.withLock {
		val historyRounds = checkNotNull(_context.value.historyRounds) { "No history rounds to compact" }
		check(rounds.all { it in historyRounds }) { "Rounds not all in current history" }
		val remaining = historyRounds.filterNot { it in rounds }
		_context.update {
			it.copy(
				compactedRounds = AgentContextIndex.CompactedRounds(
					compactedRounds = it.compactedRounds,
					rounds = rounds,
					summaryMsgRef = summary
				),
				historyRounds = remaining.orNull(),
			)
		}
	}
	
	suspend fun updateInjections(
		function: suspend (List<ContextInjection>?) -> List<ContextInjection>?
	) = lock.withLock {
		_context.update {
			val new = function(it.injections)
			if (it.injections == new) return@withLock
			it.copy(injections = new)
		}
	}
}
