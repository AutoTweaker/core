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

import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.agent.AgentToolCall
import io.github.autotweaker.api.types.agent.ToolCallStatus
import io.github.autotweaker.api.types.message.AgentMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentToolCallImpl(
	override val call: AgentMessage.Tool.Call,
	val resolved: Tool.ResolveResult.Ready?,
	result: AgentMessage.Tool.Result?,
) : AgentToolCall {
	@Volatile
	private var _result = result
	override val result get() = _result
	private val _status = MutableStateFlow(
		when {
			resolved == null && result != null -> ToolCallStatus.FINISHED
			resolved != null && result == null -> ToolCallStatus.PENDING
			else -> error("Unexpected state")
		}
	)
	override val status = _status.asStateFlow()
	
	fun waiting() = check(_status.compareAndSet(ToolCallStatus.PENDING, ToolCallStatus.WAITING))
	
	fun calling() = check(_status.compareAndSet(ToolCallStatus.WAITING, ToolCallStatus.CALLING))
	
	@Synchronized
	fun finish(result: AgentMessage.Tool.Result) {
		check(_result == null)
		_result = result
		_status.value = ToolCallStatus.FINISHED
	}
}
