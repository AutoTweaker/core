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

package io.github.autotweaker.api.types.llm

import kotlinx.serialization.json.JsonElement

data class ChatRequest(
	val model: String,
	val instructions: String?,
	val messages: List<ChatMessage>,
	val reasoning: ReasoningEffort?,
	val stream: Boolean,
	
	val maxTokens: Int?,
	val tools: List<Tool>?,
	
	val temperature: Double?,
	val jsonOutput: Boolean?,
) {
	data class Tool(
		val name: String,
		val description: String,
		val parameters: JsonElement
	)
}
