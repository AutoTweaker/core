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

import kotlin.time.Instant

sealed class ChatMessage {
	abstract val content: Any?
	abstract val timestamp: Instant
	
	data class User(
		override val content: List<ContentPart>,
		override val timestamp: Instant,
	) : ChatMessage()
	
	data class Assistant(
		override val content: String?,
		override val timestamp: Instant,
		val reasoningContent: String? = null,
		val toolCalls: List<ToolCall>? = null,
	) : ChatMessage() {
		data class ToolCall(
			val id: String,
			val name: String,
			val arguments: String
		)
	}
	
	data class ToolResult(
		override val content: String,
		override val timestamp: Instant,
		val toolCallId: String
	) : ChatMessage()
}
