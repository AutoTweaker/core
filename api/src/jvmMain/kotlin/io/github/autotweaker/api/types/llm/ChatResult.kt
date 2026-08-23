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

sealed class ChatResult {
	data class Chunk(
		val content: String?,
		val reasoningContent: String?,
		val toolCalls: List<ChunkToolCall>?,
	) : ChatResult()
	
	data class ChunkToolCall(
		val index: Int,
		val id: String?,
		val name: String?,
		val arguments: String?,
	)
	
	data class Assembled(
		val message: ChatMessage.Assistant,
		val usage: Usage? = null,
	) : ChatResult()
	
	data class Failed(
		val message: String?,
		val statusCode: Int?,
		val exception: Throwable? = null
	) : ChatResult()
}
