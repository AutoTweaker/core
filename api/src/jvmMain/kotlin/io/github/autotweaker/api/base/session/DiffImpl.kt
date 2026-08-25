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

package io.github.autotweaker.api.base.session

import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.PairList
import io.github.autotweaker.api.types.agent.AgentContext
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.agent.ContextInjection
import java.util.*

infix fun AgentContext.diff(next: AgentContext): ContextDiff? {
	if (this === next) return null
	if (this == next) return null
	return object : ContextDiff {
		override fun addedInjections(): List<ContextInjection>? {
			if (injections == null) return next.injections
			if (next.injections == null) return null
			val oldIds = injections.mapTo(mutableSetOf()) { it.id }
			return next.injections.filterNot { it.id in oldIds }.orNull()
		}
		
		override fun updatedInjections(): List<ContextInjection>? {
			if (injections == null || next.injections == null) return null
			val oldMap = injections.associateBy { it.id }
			return next.injections.filter {
				val old = oldMap[it.id] ?: return@filter false
				return@filter old != it
			}.orNull()
		}
		
		override fun removedInjections(): List<ContextInjection>? {
			if (injections == null) return null
			if (next.injections == null) return injections
			val nextIds = next.injections.mapTo(mutableSetOf()) { it.id }
			return injections.filterNot { it.id in nextIds }.orNull()
		}
		
		override fun addedCompactedRounds(): PairList<UUID, List<AgentContextIndex.CompletedRound>>? {
			if (index.compactedRounds == null) return next.index.compactedRounds?.toList()
			if (next.index.compactedRounds == null) return null
			val oldIds = mutableSetOf<UUID>()
			index.compactedRounds.forEach { oldIds.add(it.summarizedMessage) }
			return next.index.compactedRounds.toList().filterNot { it.first in oldIds }.orNull()
		}
		
		override fun addedHistoryRounds(): List<AgentContextIndex.CompletedRound>? {
			if (index.historyRounds == null) return next.index.historyRounds
			if (next.index.historyRounds == null) return null
			val oldIds = index.historyRounds.mapTo(mutableSetOf()) { it.userMessage }
			return next.index.historyRounds.filterNot { it.userMessage in oldIds }.orNull()
		}
		
		override fun removedHistoryRounds(): List<AgentContextIndex.CompletedRound>? {
			if (index.historyRounds == null) return null
			if (next.index.historyRounds == null) return index.historyRounds
			val nextIds = next.index.historyRounds.mapTo(mutableSetOf()) { it.userMessage }
			return index.historyRounds.filterNot { it.userMessage in nextIds }.orNull()
		}
		
		override fun startedRound(): AgentContextIndex.CurrentRound? {
			if (index.currentRound == null) return next.index.currentRound
			if (next.index.currentRound == null) return null
			if (index.currentRound.userMessage != next.index.currentRound.userMessage)
				return next.index.currentRound
			return null
		}
		
		override fun finishedRound(): AgentContextIndex.CurrentRound? {
			if (index.currentRound == null) return null
			if (next.index.currentRound == null) return index.currentRound
			if (index.currentRound.userMessage != next.index.currentRound.userMessage)
				return index.currentRound
			return null
		}
		
		override fun addedMessages(): Set<UUID>? {
			val nextIds = next.index.ids()
			if (nextIds.isEmpty()) return null
			val oldIds = index.ids()
			if (oldIds == nextIds) return null
			return (nextIds - oldIds).orNull()
		}
		
		override fun droppedMessages(): Set<UUID>? {
			if (droppedMessages == null) return next.droppedMessages
			if (next.droppedMessages == null) return null
			if (droppedMessages == next.droppedMessages) return null
			return (next.droppedMessages - droppedMessages).orNull()
		}
		
		override fun updatedCurrent(): ContextDiff.CurrentDiff? {
			if (index.currentRound == null || next.index.currentRound == null) return null
			with(index.currentRound) {
				val next = next.index.currentRound
				if (userMessage != next.userMessage) return null
				return object : ContextDiff.CurrentDiff {
					override fun addedTurns(): List<AgentContextIndex.Turn>? {
						if (turns == null) return next.turns
						if (next.turns == null) return null
						val oldIds = turns.mapTo(mutableSetOf()) { it.assistantMessage }
						return next.turns.filterNot { it.assistantMessage in oldIds }.orNull()
					}
					
					override fun newAssistantMessage(): UUID? {
						if (assistantMessage == null) return next.assistantMessage
						return null
					}
					
					override fun cleanedAssistantMessage(): UUID? {
						if (next.assistantMessage == null) return assistantMessage
						return null
					}
					
					override fun addedFinishedCalls(): List<AgentContextIndex.Turn.Tool>? {
						if (finishedToolCalls == null) return next.finishedToolCalls
						if (next.finishedToolCalls == null) return null
						val oldIds = finishedToolCalls.mapTo(mutableSetOf()) { it.call }
						return next.finishedToolCalls.filterNot { it.call in oldIds }.orNull()
					}
					
					override fun removedFinishedCalls(): List<AgentContextIndex.Turn.Tool>? {
						if (finishedToolCalls == null) return null
						if (next.finishedToolCalls == null) return finishedToolCalls
						val nextIds = next.finishedToolCalls.mapTo(mutableSetOf()) { it.call }
						return finishedToolCalls.filterNot { it.call in nextIds }.orNull()
					}
					
					override fun addedPendingCalls(): List<UUID>? {
						if (pendingToolCalls == null) return next.pendingToolCalls
						if (next.pendingToolCalls == null) return null
						return (next.pendingToolCalls - pendingToolCalls.toSet()).orNull()
					}
					
					override fun removedPendingCalls(): List<UUID>? {
						if (pendingToolCalls == null) return null
						if (next.pendingToolCalls == null) return pendingToolCalls
						return (pendingToolCalls - next.pendingToolCalls.toSet()).orNull()
					}
				}
			}
		}
	}
}
