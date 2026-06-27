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

package io.github.autotweaker.api.types.session

import io.github.autotweaker.api.types.serializer.UuidListSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class SessionContextIndex(
	val compactedRounds: List<CompactedRound>?,
	val historyRounds: List<CompactedRound.CompletedRound>?,
	val currentRound: CurrentRound?,
	
	@Serializable(with = UuidSerializer::class)
	val summarizedMessage: UUID?,
) {
	@Serializable
	data class CompactedRound(
		val rounds: List<CompletedRound>,
		
		@Serializable(with = UuidSerializer::class)
		val summarizedMessage: UUID,
	) {
		@Serializable
		data class CompletedRound(
			@Serializable(with = UuidSerializer::class)
			val userMessage: UUID,
			val turns: List<Turn>?,
			
			@Serializable(with = UuidSerializer::class)
			val finalAssistantMessage: UUID?,
		)
	}
	
	@Serializable
	data class CurrentRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID?,
		
		@Serializable(with = UuidListSerializer::class)
		val pendingToolCalls: List<UUID>?,
	)
	
	@Serializable
	data class Turn(
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID,
		val tools: List<Tool>,
	) {
		@Serializable
		data class Tool(
			@Serializable(with = UuidSerializer::class)
			val call: UUID,
			
			@Serializable(with = UuidSerializer::class)
			val result: UUID,
		)
	}
	
	companion object {
		fun collectCompletedRoundIds(round: CompactedRound.CompletedRound, ids: MutableSet<UUID>) {
			ids.add(round.userMessage)
			round.finalAssistantMessage?.let { ids.add(it) }
			collectTurnIds(round.turns, ids)
		}
		
		fun collectCurrentRoundIds(round: CurrentRound, ids: MutableSet<UUID>) {
			ids.add(round.userMessage)
			round.assistantMessage?.let { ids.add(it) }
			round.pendingToolCalls?.forEach { ids.add(it) }
			collectTurnIds(round.turns, ids)
		}
		
		private fun collectTurnIds(turns: List<Turn>?, ids: MutableSet<UUID>) {
			turns?.forEach { turn ->
				ids.add(turn.assistantMessage)
				turn.tools.forEach { tool ->
					ids.add(tool.call)
					ids.add(tool.result)
				}
			}
		}
	}
}