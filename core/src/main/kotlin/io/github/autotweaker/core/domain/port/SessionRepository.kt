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

package io.github.autotweaker.core.domain.port

import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionMessage
import java.util.*

interface SessionRepository {
	suspend fun saveSessions(sessionData: List<SessionData>)
	suspend fun loadSessions(ids: List<UUID>): List<SessionData>?
	suspend fun loadAllSessions(): List<SessionData>?
	suspend fun deleteSessions(id: List<UUID>)
	
	suspend fun saveAgent(agentData: AgentData)
	suspend fun loadAgent(agentId: UUID): AgentData?
	suspend fun deleteAgent(agentId: UUID)
	
	suspend fun saveMessages(messages: List<SessionMessage>)
	suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>?
	suspend fun deleteMessages(ids: List<UUID>)
}
