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

package io.github.autotweaker.api.types.agent

import io.github.autotweaker.api.tool.Tool.RuntimeOutput.OutputType
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.Usage
import java.util.*

sealed class AgentOutput {
	data class LlmDelta(
		val content: String?,
		val reasoningContent: String?,
		val toolCallFragments: List<ChatResult.ChunkToolCall>?,
	) : AgentOutput()
	
	data class LlmError(
		val content: String?,
		val statusCode: Int?,
		val exception: Throwable?,
		val model: UUID,
	) : AgentOutput()
	
	data class Compact(
		val status: Status,
		val content: String,
		val usage: Usage?,
	) : AgentOutput() {
		enum class Status {
			OUTPUTTING, FINISHED, FAILED,
		}
	}
	
	data class Tool(
		val name: String,
		val callId: String,
		val content: String,
		val type: OutputType
	) : AgentOutput()
	
	data class Error(
		val message: String,
		val type: Type,
	) : AgentOutput() {
		enum class Type {
			LLM, COMPACT,
		}
	}
}
