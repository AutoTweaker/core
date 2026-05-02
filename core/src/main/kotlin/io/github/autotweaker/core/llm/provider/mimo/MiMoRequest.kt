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

package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MiMoRequest(
	val messages: List<MiMoMessage>,
	@SerialName("tool_choice")
	val toolChoice: String? = null,
	
	override val model: String,
	override val thinking: Thinking? = null,
	@SerialName("max_completion_tokens")
	override val maxCompletionTokens: Int? = null,
	@SerialName("frequency_penalty")
	override val frequencyPenalty: Double? = null,
	@SerialName("presence_penalty")
	override val presencePenalty: Double? = null,
	@SerialName("response_format")
	override val responseFormat: ChatRequest.ResponseFormat? = null,
	override val stop: List<String>? = null,
	override val stream: Boolean? = null,
	override val temperature: Double? = null,
	@SerialName("top_p")
	override val topP: Double? = null,
	override val tools: List<Tool>? = null
) : OpenAiRequest()
