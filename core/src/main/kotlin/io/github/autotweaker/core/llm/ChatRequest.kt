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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

data class ChatRequest(
	val model: String,
	val messages: List<ChatMessage>,
	val thinking: Boolean? = null,
	val stream: Boolean = false,
	
	val maxTokens: Int? = null,
	val tools: List<Tool>? = null,
	val toolCallRequired: Boolean? = null,
	
	val temperature: Double? = null,
	val topP: Double? = null,
	val frequencyPenalty: Double? = null,
	val presencePenalty: Double? = null,
	val responseFormat: ResponseFormat? = null
) {
	data class Tool(
		val name: String,
		val description: String,
		val parameters: JsonElement
	)
	
	@Serializable
	data class ResponseFormat(
		val type: Type
	) {
		@Serializable
		
		enum class Type {
			@SerialName("text")
			TEXT,
			
			@SerialName("json_object")
			JSON_OBJECT
		}
	}
}
