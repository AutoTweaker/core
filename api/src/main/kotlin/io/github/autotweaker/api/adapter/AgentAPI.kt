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

package io.github.autotweaker.api.adapter

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionOutput
import io.github.autotweaker.api.types.tool.ToolApprove
import io.github.autotweaker.api.types.tool.ToolInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

interface AgentAPI {
	val id: UUID
	
	val model: ModelConfig
	val status: StateFlow<AgentStatus>
	val output: SharedFlow<SessionOutput>
	val context: StateFlow<SessionContext>
	val toolInfo: StateFlow<List<ToolInfo>>
	
	fun send(content: MessageContent)
	
	suspend fun pause()
	suspend fun stop()
	suspend fun compact()
	
	suspend fun inject(injections: List<ContextInjection>?)
	suspend fun setModel(config: ModelConfig)
	suspend fun approve(approval: ToolApprove)
	
	suspend fun cancelCompact()
	suspend fun cancelTool()
}
