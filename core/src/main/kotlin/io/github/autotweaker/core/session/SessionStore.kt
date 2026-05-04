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

import java.util.*

@Suppress("unused")
interface SessionStore {
	suspend fun saveSessions(sessionData: List<SessionData>)
	suspend fun loadSessions(ids: List<UUID>): List<SessionData>?
	suspend fun loadAllSessions(): List<SessionData>?
	suspend fun deleteSessions(id: List<UUID>)
	
	suspend fun saveContext(sessionId: UUID, context: SessionContext)
	suspend fun loadContext(sessionId: UUID): SessionContext?
	suspend fun deleteContext(sessionId: UUID)
	
	suspend fun saveMessages(messages: List<SessionMessage>)
	suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>?
	suspend fun deleteMessages(ids: List<UUID>)
}
