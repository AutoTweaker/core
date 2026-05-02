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
import io.github.autotweaker.core.llm.base.openai.OpenAiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DeepSeekResponse(
	val choices: List<Choice>,
	val usage: DeepSeekUsage,
	override val id: String,
	@Serializable(with = InstantAsLongSerializer::class)
	override val created: Instant,
	override val model: String
) : OpenAiResponse() {
	@Serializable
	data class Choice(
		val index: Int,
		val message: DeepSeekMessage.AssistantMessage,
		@SerialName("finish_reason")
		val finishReason: DeepSeekFinishReason,
	)
}
