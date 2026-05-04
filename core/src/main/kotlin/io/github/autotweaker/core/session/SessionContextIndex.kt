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

import java.util.*

data class SessionContextIndex(
	val compactedRounds: List<CompactedRound>?,
	val historyRounds: List<CompactedRound.CompletedRound>?,
	val currentRound: CurrentRound?,
	val summarizedMessage: UUID?,
) {
	data class CompactedRound(
		val rounds: List<CompletedRound>,
		val summarizedMessage: UUID,
	) {
		data class CompletedRound(
			val userMessage: UUID,
			val turns: List<Turn>?,
			val finalAssistantMessage: UUID?,
		)
	}
	
	data class CurrentRound(
		val userMessage: UUID,
		val turns: List<Turn>?,
		val assistantMessage: UUID?,
		val pendingToolCalls: List<UUID>?,
	)
	
	data class Turn(
		val assistantMessage: UUID,
		val tools: List<Tool>,
	) {
		data class Tool(
			val call: UUID,
			val result: UUID,
		)
	}
}
