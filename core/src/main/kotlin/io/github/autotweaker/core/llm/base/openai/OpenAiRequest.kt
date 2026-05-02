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

package io.github.autotweaker.core.llm.base.openai

import io.github.autotweaker.core.llm.ChatRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

abstract class OpenAiRequest {
	abstract val model: String?
	abstract val thinking: Thinking?
	abstract val frequencyPenalty: Double?
	abstract val presencePenalty: Double?
	abstract val responseFormat: ChatRequest.ResponseFormat?
	abstract val stop: List<String>?
	abstract val stream: Boolean?
	abstract val temperature: Double?
	abstract val topP: Double?
	abstract val maxCompletionTokens: Int?
	abstract val tools: List<Tool>?
	
	@Serializable
	data class Thinking(
		val type: Type
	) {
		@Serializable
		enum class Type {
			@SerialName("enabled")
			ENABLED,
			
			@SerialName("disabled")
			DISABLED
		}
	}
	
	@Serializable
	data class Tool(
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
}
