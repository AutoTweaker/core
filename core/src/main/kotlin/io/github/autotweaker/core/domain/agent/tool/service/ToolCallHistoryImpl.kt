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

package io.github.autotweaker.core.domain.agent.tool.service

import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory

class ToolCallHistoryImpl(
	private val env: AgentEnvironment,
) : ToolCallHistory {
	
	override fun getAll(): List<ToolCallHistory.Entry> = buildList {
		val ctx = env.context.value
		for (round in ctx.historyRounds.orEmpty()) {
			for (turn in round.turns.orEmpty()) {
				for (tool in turn.tools) {
					add(
						ToolCallHistory.Entry(
							name = tool.name,
							arguments = tool.call.arguments,
							resultContent = tool.result.content,
						)
					)
				}
			}
		}
		for (turn in ctx.currentRound?.turns.orEmpty()) {
			for (tool in turn.tools) {
				add(
					ToolCallHistory.Entry(
						name = tool.name,
						arguments = tool.call.arguments,
						resultContent = tool.result.content,
					)
				)
			}
		}
	}
}
