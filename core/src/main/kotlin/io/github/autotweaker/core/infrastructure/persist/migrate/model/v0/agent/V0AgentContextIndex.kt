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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent

import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class V0AgentContextIndex(
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<CompletedRound>?,
	val currentRound: CurrentRound?,
) {
	fun ids(): Set<UUID> =
		compactedRounds?.ids().orEmpty() +
				historyRounds?.flatMap { it.ids() }.orEmpty() +
				currentRound?.ids().orEmpty()
	
	@Serializable
	data class CompactedRounds(
		val compactedRounds: CompactedRounds?,
		val rounds: List<CompletedRound>,
		@Serializable(with = UuidSerializer::class)
		val summarizedMessage: UUID,
	) {
		fun ids(): Set<UUID> =
			compactedRounds?.ids().orEmpty() +
					rounds.flatMap { it.ids() } +
					setOf(summarizedMessage)
	}
	
	@Serializable
	data class CompletedRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		@Serializable(with = UuidSerializer::class)
		val finalAssistantMessage: UUID?,
	) {
		fun ids(): Set<UUID> =
			setOf(userMessage) +
					turns?.flatMap { it.ids() }.orEmpty() +
					setOfNotNull(finalAssistantMessage)
	}
	
	@Serializable
	data class CurrentRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID?,
		val finishedToolCalls: List<Turn.Tool>?,
		val pendingToolCalls: List<@Serializable(with = UuidSerializer::class) UUID>?,
	) {
		fun ids(): Set<UUID> =
			setOf(userMessage) +
					turns?.flatMap { it.ids() }.orEmpty() +
					setOfNotNull(assistantMessage) +
					finishedToolCalls?.flatMap { it.ids() }.orEmpty() +
					pendingToolCalls.orEmpty()
	}
	
	@Serializable
	data class Turn(
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID,
		val tools: List<Tool>,
	) {
		fun ids(): Set<UUID> =
			setOf(assistantMessage) +
					tools.flatMap { it.ids() }
		
		@Serializable
		data class Tool(
			@Serializable(with = UuidSerializer::class)
			val call: UUID,
			@Serializable(with = UuidSerializer::class)
			val result: UUID,
		) {
			fun ids(): Set<UUID> = setOf(call, result)
		}
	}
}
