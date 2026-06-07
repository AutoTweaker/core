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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
class ToolCallHistoryImpl(
	private val env: AgentEnvironment,
) : ToolCallHistory {
	private val json = Json {
		namingStrategy = JsonNamingStrategy.SnakeCase
	}
	
	override fun <Args : Any> getAll(
		toolName: String,
		argsSerializer: KSerializer<Args>
	): List<ToolCallHistory.Entry<Args>> = buildList {
		val ctx = env.context.value
		for (round in ctx.historyRounds.orEmpty()) {
			for (turn in round.turns.orEmpty()) {
				for (tool in turn.tools) {
					val args = tryDeserialize(toolName, tool.name, tool.call.arguments, argsSerializer) ?: continue
					add(ToolCallHistory.Entry(args, tool.result.content))
				}
			}
		}
		for (turn in ctx.currentRound?.turns.orEmpty()) {
			for (tool in turn.tools) {
				val args = tryDeserialize(toolName, tool.name, tool.call.arguments, argsSerializer) ?: continue
				add(ToolCallHistory.Entry(args, tool.result.content))
			}
		}
	}
	
	private fun <Args : Any> tryDeserialize(
		expectedToolName: String,
		callName: String,
		arguments: String,
		argsSerializer: KSerializer<Args>,
	): Args? {
		val parts = callName.split("-", limit = 2)
		if (parts.size != 2 || parts[0] != expectedToolName) return null
		val functionName = parts[1]
		val base = runCatching { Json.parseToJsonElement(arguments) as? JsonObject }.getOrNull() ?: return null
		val deserializationJson = if (argsSerializer.descriptor.kind == PolymorphicKind.SEALED) {
			JsonObject(base + ("type" to JsonPrimitive(functionName)))
		} else {
			base
		}
		return runCatching { json.decodeFromJsonElement(argsSerializer, deserializationJson) }.getOrNull()
	}
}
