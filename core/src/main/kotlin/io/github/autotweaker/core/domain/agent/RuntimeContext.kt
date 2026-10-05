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

import io.github.autotweaker.api.types.agent.AgentContextIndex.CompactedRounds
import io.github.autotweaker.api.types.agent.AgentContextIndex.Round
import io.github.autotweaker.api.types.message.ContextInjection

data class RuntimeContext(
	val systemPrompt: String?,
	val injections: List<ContextInjection>?,
	val compactedRounds: CompactedRounds?,
	val historyRounds: List<Round>?,
	val currentRound: Round?,
) {
	fun ids() = buildSet {
		compactedRounds?.forEach { it.idsTo(this) }
		historyRounds?.forEach { it.idsTo(this) }
		currentRound?.idsTo(this)
	}
	
	fun rounds() = buildList {
		historyRounds?.let { addAll(it) }
		currentRound?.let { add(it) }
	}
}
