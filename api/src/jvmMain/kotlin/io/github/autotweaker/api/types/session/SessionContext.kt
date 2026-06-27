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

import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.serializer.UuidListSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class SessionContext(
	val systemPrompt: String,
	val injections: List<ContextInjection>?,
	val index: SessionContextIndex,
	@Serializable(with = UuidListSerializer::class)
	val droppedMessages: List<UUID>?,
) {
	companion object {
		fun emptyContext(systemPrompt: String) = SessionContext(
			systemPrompt = systemPrompt, index = SessionContextIndex(
				compactedRounds = null, historyRounds = null, currentRound = null, summarizedMessage = null
			), droppedMessages = null, injections = null
		)
	}
}
