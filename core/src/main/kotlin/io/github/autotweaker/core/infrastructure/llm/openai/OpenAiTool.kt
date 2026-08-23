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

package io.github.autotweaker.core.infrastructure.llm.openai

import io.github.autotweaker.api.types.llm.ChatRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAiTool(
	val type: String = "function",
	val function: Function
) {
	@Serializable
	data class Function(
		val name: String,
		val description: String?,
		val parameters: JsonElement,
		val strict: Boolean? = null
	)
}

fun List<ChatRequest.Tool>.transform() = map {
	OpenAiTool(
		function = OpenAiTool.Function(
			name = it.name, description = it.description, parameters = it.parameters
		)
	)
}
