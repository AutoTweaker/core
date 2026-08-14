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
	
	private val pendingToolResults = mutableListOf<ToolMessage>()
	
	suspend fun get(): RuntimeContext = lock.withLock { _context.value }
	
	suspend fun beginRound(userMessage: RuntimeContext.Message.User) = lock.withLock {
		check(_context.value.currentRound == null)
		check(pendingToolResults.isEmpty())
		val round = RuntimeContext.CurrentRound(
			userMessage = userMessage,
			turns = null,
			assistantMessage = null,
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
		check(pendingToolResults.isEmpty())
		pendingToolResults.addAll(immediateResults)
		_context.update {
			it.copy(
				currentRound = current.copy(assistantMessage = assistant, pendingToolCalls = pendingCalls)
			)
		}
	}
	
	suspend fun cancelPending(presentation: (callId: String) -> ToolPresentation) = lock.withLock {
		val current = _context.value.currentRound ?: return@withLock
		current.pendingToolCalls ?: return@withLock
		val processedIds = pendingToolResults.map { it.callId }.toSet()
		val remaining = current.pendingToolCalls.filter { it.callId !in processedIds }
		remaining.forEach {
			recordToolMessage(
				buildCancelled(
					it,
					ToolSettings.CancelledPending().get(),
					presentation(it.callId),
				)
			)
		}
	}
	
	suspend fun recordToolMessage(tool: ToolMessage) = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		checkNotNull(current.pendingToolCalls)
		check(current.pendingToolCalls.any { it.callId == tool.callId })
		pendingToolResults.add(tool)
	}
	
	suspend fun finalizeToolTurn() = lock.withLock {
		val current = requireNotNull(_context.value.currentRound)
		val assistant = requireNotNull(current.assistantMessage)
		val turn = RuntimeContext.Turn(assistantMessage = assistant, tools = pendingToolResults.toList())
		val processedIds = pendingToolResults.map { it.callId }.toSet()
		val remaining = current.pendingToolCalls.orEmpty().filter { it.callId !in processedIds }
		pendingToolResults.clear()
		_context.update {
			it.copy(
				currentRound = current.copy(
					turns = current.turns.orEmpty() + turn,
					assistantMessage = null,
					pendingToolCalls = remaining.orNull(),
				)
			)
		}
	}
	
	suspend fun archiveCurrentRound() = lock.withLock {
		val round = _context.value.currentRound ?: return@withLock
		
		//丢弃空round
		if (round.assistantMessage == null
			&& round.turns.isNullOrEmpty()
			&& round.pendingToolCalls.isNullOrEmpty()
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
					buildCancelled(
						call = call,
						message = ToolSettings.CancelledPending().get(),
						presentation = listOf(
							UiBlock.Text(
								i18n(
									ToolI18n.Cancelled(),
									call.validatedToolName
								)
							)
						),
					)
				)
			}
		}
		
		val assistantMsg = round.assistantMessage
		val archivedTurn =
			if (assistantMsg != null && pendingToolResults.isNotEmpty())
				RuntimeContext.Turn(assistantMsg, pendingToolResults.toList())
			else null
		pendingToolResults.clear()
		
		val allTurns = buildList {
			round.turns?.let { addAll(it) }
			archivedTurn?.let { add(it) }
		}.orNull()
		
		val completed = RuntimeContext.CompletedRound(
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
		function: (List<ContextInjection>?) -> List<ContextInjection>?
	) = lock.withLock {
		_context.update {
			val new = function(it.injections)
			if (it.injections == new) return@withLock
			it.copy(injections = new)
		}
	}
}
