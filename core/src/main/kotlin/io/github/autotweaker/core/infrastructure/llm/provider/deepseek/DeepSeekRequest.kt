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

package io.github.autotweaker.core.infrastructure.llm.provider.deepseek

import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiResponseFormat
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiThinking
import io.github.autotweaker.core.infrastructure.llm.openai.OpenAiTool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekRequest(
	val messages: List<DeepSeekMessage>,
	@SerialName("stream_options")
	val streamOptions: StreamOptions?,
	val tools: List<OpenAiTool>?,
	@SerialName("tool_choice")
	val toolChoice: String?,
	val model: String,
	val thinking: OpenAiThinking?,
	@SerialName("reasoning_effort")
	val reasoningEffort: Effort?,
	@SerialName("max_tokens")
	val maxTokens: Int?,
	@SerialName("response_format")
	val responseFormat: OpenAiResponseFormat?,
	val stream: Boolean?,
	val temperature: Double?,
) {
	@Serializable
	data class StreamOptions(
		@SerialName("include_usage")
		val includeUsage: Boolean = true
	)
	
	@Serializable
	enum class Effort {
		@SerialName("low")
		LOW,
		
		@SerialName("high")
		HIGH,
		
		@SerialName("max")
		MAX
	}
}
