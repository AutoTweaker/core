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

package io.github.autotweaker.api.types.session

import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.StreamDelta
import io.github.autotweaker.api.types.tool.ToolOutput
import java.util.*
import kotlin.time.Instant

sealed class SessionOutput {
	data class LlmDelta(val delta: StreamDelta) : SessionOutput()
	data class LlmError(
		val content: String?,
		val statusCode: Int?,
		val model: UUID,
		val timestamp: Instant,
	) : SessionOutput()
	
	data class Compact(
		val output: CompactOutput
	) : SessionOutput()
	
	data class Tool(
		val output: ToolOutput
	) : SessionOutput()
	
	data class Error(
		val error: AgentError
	) : SessionOutput()
}
