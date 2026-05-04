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

package io.github.autotweaker.core.llm

sealed class ChatResult {
	abstract val message: ChatMessage?
	abstract val finishReason: FinishReason?
	abstract val usage: Usage?
	
	data class Chunk(
		override val message: ChatMessage.AssistantMessage? = null,
		val toolCalls: List<ChunkToolCall>? = null,
		override val finishReason: FinishReason? = null,
		override val usage: Usage? = null,
	) : ChatResult()
	
	data class ChunkToolCall(
		val index: Int,
		val id: String? = null,
		val name: String? = null,
		val arguments: String? = null,
	)
	
	data class Assembled(
		override val message: ChatMessage,
		override val finishReason: FinishReason? = null,
		override val usage: Usage? = null,
	) : ChatResult()
	
	data class FinishReason(
		val reason: String,
		val type: Type
	) {
		enum class Type {
			STOP, TOOL, ERROR, FILTER, LENGTH
		}
	}
}
