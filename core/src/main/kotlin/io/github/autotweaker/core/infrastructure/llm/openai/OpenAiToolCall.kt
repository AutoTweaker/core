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

import io.github.autotweaker.api.types.llm.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiToolCall(
	val id: String,
	val type: String = "function",
	val function: Function
) {
	@Serializable
	data class Function(
		val name: String,
		val arguments: String
	)
}

@JvmName("toOpenAiToolCalls")
fun List<ChatMessage.Assistant.ToolCall>.transform() = map {
	OpenAiToolCall(
		id = it.id, function = OpenAiToolCall.Function(
			name = it.name, arguments = it.arguments
		)
	)
}

@JvmName("toChatToolCalls")
fun List<OpenAiToolCall>.transform() = map {
	ChatMessage.Assistant.ToolCall(
		id = it.id, name = it.function.name, arguments = it.function.arguments
	)
}
