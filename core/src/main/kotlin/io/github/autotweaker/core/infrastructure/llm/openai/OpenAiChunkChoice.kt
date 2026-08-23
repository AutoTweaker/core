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

import io.github.autotweaker.api.types.llm.ChatResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChunkChoice(
	val index: Int,
	val delta: Delta,
) {
	@Serializable
	data class Delta(
		val content: String? = null,
		@SerialName("reasoning_content")
		val reasoningContent: String? = null,
		@SerialName("tool_calls")
		val toolCalls: List<ChunkCall>? = null
	)
	
	@Serializable
	data class ChunkCall(
		val index: Int,
		val id: String? = null,
		val type: String? = null,
		val function: Function? = null
	) {
		@Serializable
		data class Function(
			val name: String? = null,
			val arguments: String? = null
		)
	}
}

fun List<OpenAiChunkChoice.ChunkCall>.transform() = map {
	ChatResult.ChunkToolCall(
		index = it.index, id = it.id, name = it.function?.name, arguments = it.function?.arguments
	)
}
