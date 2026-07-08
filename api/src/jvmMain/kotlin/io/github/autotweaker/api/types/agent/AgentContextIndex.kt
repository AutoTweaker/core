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

package io.github.autotweaker.api.types.agent

import io.github.autotweaker.api.types.serializer.UuidListSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AgentContextIndex(
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<CompletedRound>?,
	val currentRound: CurrentRound?,
) : UuidIndex {
	override fun ids(): Set<UUID> =
		compactedRounds?.ids().orEmpty() +
				historyRounds?.flatMap { it.ids() }.orEmpty() +
				currentRound?.ids().orEmpty()
	
	@Serializable
	data class CompactedRounds(
		val compactedRounds: CompactedRounds?,
		val rounds: List<CompletedRound>,
		
		@Serializable(with = UuidSerializer::class)
		val summarizedMessage: UUID,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			compactedRounds?.ids().orEmpty() +
					rounds.flatMap { it.ids() } +
					setOf(summarizedMessage)
		
		fun forEach(block: (CompactedRounds) -> Unit) {
			compactedRounds?.forEach(block)
			block(this)
		}
	}
	
	@Serializable
	data class CompletedRound(
		@Serializable(with = UuidSerializer::class)
		val userMessage: UUID,
		val turns: List<Turn>?,
		
		@Serializable(with = UuidSerializer::class)
		val finalAssistantMessage: UUID?,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
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
		
		@Serializable(with = UuidListSerializer::class)
		val pendingToolCalls: List<UUID>?,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			setOf(userMessage) +
					turns?.flatMap { it.ids() }.orEmpty() +
					setOfNotNull(assistantMessage) +
					pendingToolCalls.orEmpty()
	}
	
	@Serializable
	data class Turn(
		@Serializable(with = UuidSerializer::class)
		val assistantMessage: UUID,
		val tools: List<Tool>,
	) : UuidIndex {
		override fun ids(): Set<UUID> =
			setOf(assistantMessage) +
					tools.flatMap { it.ids() }
		
		@Serializable
		data class Tool(
			@Serializable(with = UuidSerializer::class)
			val call: UUID,
			
			@Serializable(with = UuidSerializer::class)
			val result: UUID,
		) : UuidIndex {
			override fun ids(): Set<UUID> = setOf(call, result)
		}
	}
}
