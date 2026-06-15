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

package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.tool.ToolOutput
import io.github.autotweaker.core.domain.agent.chat.AgentChatStreamResult
import kotlin.time.Instant

sealed class AgentOutput {
	data class LlmDelta(val delta: StreamDelta) : AgentOutput()
	data class LlmError(val error: AgentChatStreamResult.Failing.Error) : AgentOutput()
	
	data class Compact(
		val output: CompactOutput
	) : AgentOutput()
	
	data class Tool(
		val output: ToolOutput
	) : AgentOutput()
	
	data class Error(
		val error: AgentError
	) : AgentOutput()
	
	data class UsageConsumed(
		val timestamp: Instant,
		val usage: Usage,
		val modelInfo: ModelData.ModelInfo,
	) : AgentOutput()
}
