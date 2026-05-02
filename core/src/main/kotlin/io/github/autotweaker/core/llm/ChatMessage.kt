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

import io.github.autotweaker.core.Base64
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed class ChatMessage {
	abstract val content: String?
	abstract val createdAt: Instant
	
	data class SystemMessage(
		override val content: String,
		override val createdAt: Instant
	) : ChatMessage()
	
	data class UserMessage(
		override val content: String,
		override val createdAt: Instant,
		val pictures: List<Base64>? = null
	) : ChatMessage()
	
	data class AssistantMessage(
		override val content: String?,
		override val createdAt: Instant,
		val reasoningContent: String? = null,
		val toolCalls: List<ToolCall>? = null,
		val model: String? = null
	) : ChatMessage() {
		data class ToolCall(
			val id: String,
			val name: String,
			val arguments: String
		)
	}
	
	data class ToolMessage(
		override val content: String,
		override val createdAt: Instant,
		val toolCallId: String
	) : ChatMessage()
	
	data class ErrorMessage(
		override val content: String?,
		override val createdAt: Instant,
		val statusCode: HttpStatusCode?,
	) : ChatMessage()
}
