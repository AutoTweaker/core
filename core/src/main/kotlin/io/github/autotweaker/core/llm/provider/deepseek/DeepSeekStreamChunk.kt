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

package io.github.autotweaker.core.llm.provider.deepseek

import io.github.autotweaker.core.llm.base.openai.InstantAsLongSerializer
import io.github.autotweaker.core.llm.base.openai.OpenAiStreamChunk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DeepSeekStreamChunk(
	val choices: List<Choice>,
	val usage: DeepSeekUsage? = null,
	override val id: String,
	@Serializable(with = InstantAsLongSerializer::class)
	override val created: Instant,
	override val model: String,
) : OpenAiStreamChunk() {
	
	@Serializable
	data class Choice(
		val index: Int,
		val delta: Delta,
		@SerialName("finish_reason")
		val finishReason: DeepSeekFinishReason? = null
	) {
		@Serializable
		data class Delta(
			val role: String? = null,
			val content: String? = null,
			@SerialName("reasoning_content")
			val reasoningContent: String? = null,
			@SerialName("tool_calls")
			val toolCalls: List<ToolCall>? = null
		)
		
		@Serializable
		data class ToolCall(
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
}
