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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1.session

import io.github.autotweaker.api.andLog
import io.github.autotweaker.api.json
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentIndex
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0AgentTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0SessionTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.file.Files
import java.util.*

object SessionPlanner : MigratorBase() {
	suspend fun plan(): SessionPlan? {
		if (!Files.exists(DB_PATH.resolve("$SESSIONS_DB.mv.db"))) return null
		val v0Sessions = V0SessionTable("session_data")
		val v0Agents = V0AgentTable("agent_data")
		
		val allSessions = transaction(SESSIONS_DB) {
			v0Sessions.selectAll().map { row ->
				val index = json.decodeFromString<V0AgentIndex>(row[v0Sessions.agentIndexJson])
				check(index.main.children.isEmpty()) {
					"Unexpected child agents in session  session=${row[v0Sessions.id]}"
				}
				SessionWrite(
					id = row[v0Sessions.id],
					title = row[v0Sessions.title],
					overview = row[v0Sessions.overview],
					workspaceId = row[v0Sessions.workspaceId],
					mainId = index.main.id,
					indexJson = row[v0Sessions.agentIndexJson],
				)
			}
		}
		val allAgents = transaction(SESSIONS_DB) {
			v0Agents.selectAll().map { row ->
				AgentWrite(
					id = row[v0Agents.id],
					name = row[v0Agents.name],
					sessionId = row[v0Agents.id],
					modelJson = row[v0Agents.modelJson],
					contextJson = row[v0Agents.contextJson],
					activeToolsJson = row[v0Agents.activeToolsJson],
				)
			}
		}
		
		val sessionOfMain = allSessions.associate { it.mainId to it.id }
		allSessions.forEach { session ->
			check(allAgents.any { it.id == session.mainId }) {
				"Missing main agent  session=${session.id}"
			}
		}
		allAgents.forEach { agent ->
			check(sessionOfMain.containsKey(agent.id)) {
				"Orphan agent  agent=${agent.id}"
			}
		}
		return SessionPlan(
			sessions = allSessions,
			agents = allAgents.map { it.copy(sessionId = sessionOfMain.getValue(it.id)) },
		).andLog(log) {
			info("Planned session migration  sessions={}  agents={}", it.sessions.size, it.agents.size)
		}
	}
	
	data class SessionWrite(
		val id: UUID,
		val title: String?,
		val overview: String?,
		val workspaceId: UUID,
		val mainId: UUID,
		val indexJson: String,
	)
	
	data class AgentWrite(
		val id: UUID,
		val name: String,
		val sessionId: UUID,
		val modelJson: String,
		val contextJson: String,
		val activeToolsJson: String,
	)
	
	data class SessionPlan(
		val sessions: List<SessionWrite>,
		val agents: List<AgentWrite>,
	)
}
