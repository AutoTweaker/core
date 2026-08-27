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
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.tool.ToolPresentation
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.runner.ToolMessageFactory.buildCancelled
import io.github.autotweaker.core.domain.agent.tool.ToolI18n
import io.github.autotweaker.core.domain.agent.tool.ToolSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import io.github.autotweaker.core.domain.agent.RuntimeContext.Message.Tool as ToolMessage

class AgentContextManager(initial: RuntimeContext) : I18nable {
	private val _context = MutableStateFlow(initial)
	val context: StateFlow<RuntimeContext> = _context.asStateFlow()
	
	private val lock = ReentrantMutex()
	private val cancelledPending = ToolSettings.CancelledPending().get()
	
	suspend fun get(): RuntimeContext = lock.withLock { _context.value }
	
	suspend fun beginRound(userMessage: RuntimeContext.Message.User) = lock.withLock {
		check(_context.value.currentRound == null)
		val round = RuntimeContext.CurrentRound(
			userMessage = userMessage,
			turns = null,
			assistantMessage = null,
			finishedToolCalls = null,
			pendingToolCalls = null,
		)
		_context.update { it.copy(currentRound = round) }
	}
	
	suspend fun applyThinking(
		assistant: RuntimeContext.Message.Assistant,
		pendingCalls: List<RuntimeContext.CurrentRound.PendingToolCall>?,
		immediateResults: List<ToolMessage>,
	) = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		check(current.assistantMessage == null)
		check(current.pendingToolCalls == null)
		check(current.finishedToolCalls == null)
		_context.update {
			it.copy(
				currentRound = current.copy(
					assistantMessage = assistant,
					finishedToolCalls = immediateResults,
					pendingToolCalls = pendingCalls
				)
			)
		}
	}
	
	suspend fun cancelPending(presentation: (callId: String) -> ToolPresentation) = lock.withLock {
		val current = _context.value.currentRound ?: return@withLock
		current.pendingToolCalls ?: return@withLock
		_context.update { ctx ->
			ctx.copy(
				currentRound = current.copy(
					finishedToolCalls = current.finishedToolCalls.orEmpty() + current.pendingToolCalls.map {
						buildCancelled(
							it,
							cancelledPending,
							presentation(it.callId),
						)
					},
					pendingToolCalls = null
				)
			)
		}
	}
	
	suspend fun recordToolMessage(tool: ToolMessage) = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		checkNotNull(current.pendingToolCalls)
		check(current.pendingToolCalls.any { it.callId == tool.callId })
		_context.update { ctx ->
			ctx.copy(
				currentRound = current.copy(
					finishedToolCalls = current.finishedToolCalls.orEmpty() + tool,
					pendingToolCalls = current.pendingToolCalls.filterNot { it.callId == tool.callId }.orNull()
				)
			)
		}
	}
	
	suspend fun finalizeToolTurn() = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		val assistant = requireNotNull(current.assistantMessage)
		checkNotNull(current.finishedToolCalls)
		check(current.pendingToolCalls.isNullOrEmpty())
		val turn = RuntimeContext.Turn(assistantMessage = assistant, tools = current.finishedToolCalls)
		
		_context.update {
			it.copy(
				currentRound = current.copy(
					turns = current.turns.orEmpty() + turn,
					assistantMessage = null,
					finishedToolCalls = null,
					pendingToolCalls = null,
				)
			)
		}
	}
	
	suspend fun archiveCurrentRound() = lock.withLock {
		val round = _context.value.currentRound ?: return@withLock
		
		//丢弃空round
		if (round.assistantMessage == null
			&& round.turns.isNullOrEmpty()
			&& round.finishedToolCalls.isNullOrEmpty()
			&& round.pendingToolCalls.isNullOrEmpty()
		) {
			_context.update { it.copy(currentRound = null) }
			return@withLock
		}
		
		val assistantMsg = round.assistantMessage
		val toolMessages = round.finishedToolCalls.orEmpty() + round.pendingToolCalls?.map { call ->
			buildCancelled(
				call = call,
				message = cancelledPending,
				presentation = listOf(
					UiBlock.Text(
						i18n(
							ToolI18n.Cancelled(),
							call.validatedToolName
						)
					)
				),
			)
		}.orEmpty()
		
		val archivedTurn = if (assistantMsg != null && toolMessages.isNotEmpty())
			RuntimeContext.Turn(assistantMsg, toolMessages)
		else null
		
		val allTurns = if (round.turns.isNullOrEmpty() && archivedTurn == null) null else buildList {
			round.turns?.let { addAll(it) }
			archivedTurn?.let { add(it) }
		}.orNull()
		
		val completed = RuntimeContext.CompletedRound(
			userMessage = round.userMessage,
			turns = allTurns,
			finalAssistantMessage = if (archivedTurn != null) null else assistantMsg, // 如果没archivedTurn，assistantMsg才是真正的final
		)
		_context.update {
			it.copy(
				currentRound = null,
				historyRounds = it.historyRounds.orEmpty() + completed,
			)
		}
	}
	
	suspend fun applyCompact(
		summarizedMessage: RuntimeContext.SummarizedMessage,
		rounds: List<RuntimeContext.CompletedRound>,
	) = lock.withLock {
		val currentHistory = requireNotNull(_context.value.historyRounds)
		check(rounds.all { it in currentHistory })
		val remaining = currentHistory.filterNot { it in rounds }
		_context.update {
			it.copy(
				compactedRounds = RuntimeContext.CompactedRounds(
					compactedRounds = it.compactedRounds,
					rounds = rounds,
					summarizedMessage = summarizedMessage
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
