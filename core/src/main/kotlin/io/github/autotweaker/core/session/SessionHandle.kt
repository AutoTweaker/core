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

package io.github.autotweaker.core.session

import io.github.autotweaker.api.types.agent.AgentStatus
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.agent.AgentOutput
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

data class SessionHandle(
	val id: UUID,
	val context: StateFlow<SessionContext>,
	val output: SharedFlow<AgentOutput>,
	val status: StateFlow<AgentStatus>,
	val data: StateFlow<SessionData>,
) {
	companion object {
		fun fromSession(session: Session) = SessionHandle(
			id = session.data.value.id,
			context = session.context,
			output = session.output,
			status = session.agentStatus,
			data = session.data,
		)
	}
}