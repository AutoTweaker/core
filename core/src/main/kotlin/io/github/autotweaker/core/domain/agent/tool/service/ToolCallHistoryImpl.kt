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

import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
class ToolCallHistoryImpl(
	private val context: RuntimeContext,
) : ToolCallHistory {
	override fun <Args : Any> getAll(
		toolName: String,
		argsSerializer: KSerializer<Args>
	): List<ToolCallHistory.Entry<Args>> = buildList {
		for (round in context.historyRounds.orEmpty()) {
			for (turn in round.turns.orEmpty()) {
				for (tool in turn.tools) {
					val args = tryDeserialize(toolName, tool.name, tool.call.validatedArgs ?: continue, argsSerializer)
						?: continue
					add(ToolCallHistory.Entry(args, tool.result.content))
				}
			}
		}
		for (turn in context.currentRound?.turns.orEmpty()) {
			for (tool in turn.tools) {
				val args =
					tryDeserialize(toolName, tool.name, tool.call.validatedArgs ?: continue, argsSerializer) ?: continue
				add(ToolCallHistory.Entry(args, tool.result.content))
			}
		}
	}
	
	private fun <Args : Any> tryDeserialize(
		expectedToolName: String,
		callName: String,
		arguments: JsonElement,
		argsSerializer: KSerializer<Args>,
	): Args? {
		val toolName = callName.substringBefore("-")
		if (toolName != expectedToolName) return null
		return Json.decodeFromJsonElement(argsSerializer, arguments)
	}
}
