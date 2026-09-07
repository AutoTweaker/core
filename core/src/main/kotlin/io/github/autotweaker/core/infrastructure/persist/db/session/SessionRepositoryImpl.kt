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

package io.github.autotweaker.core.infrastructure.persist.db.session

import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

class SessionRepositoryImpl(store: DatabaseStore) : SessionRepository,
	DbStore(
		store, "Sessions",
		SessionDataTable, AgentDataTable, SessionMessageTable
	) {
	override suspend fun saveSessions(sessionData: List<SessionData>) {
		db.transaction {
			sessionData.forEach { data ->
				SessionDataTable.upsert {
					it[id] = data.id
					it[title] = data.title
					it[overview] = data.overview
					it[workspaceId] = data.workspaceId
					it[creationTime] = data.creationTime
					it[lastAccessTime] = data.lastAccessTime
					SessionDataTable.fillAgentIndex(it, data.agentIndex)
				}
			}
		}
	}
	
	override suspend fun loadSessions(ids: Set<UUID>): List<SessionData> =
		db.transaction {
			SessionDataTable.selectAll()
				.where { SessionDataTable.id inList ids }
				.map { it.toSessionData() }
		}
	
	override suspend fun loadAllSessions(): List<SessionData> =
		db.transaction {
			SessionDataTable.selectAll()
				.map { it.toSessionData() }
		}
	
	override suspend fun deleteSessions(id: Set<UUID>) {
		db.transaction {
			val agentIds = AgentDataTable.selectAll()
				.where { AgentDataTable.sessionId inList id }
				.map { it[AgentDataTable.id] }
				.toSet()
			AgentDataTable.deleteWhere { AgentDataTable.sessionId inList id }
			if (agentIds.isNotEmpty()) {
				SessionMessageTable.selectAll()
					.where {
						agentIds.map { agentId ->
							QueryParameter(agentId, UUIDColumnType()) eq anyFrom(SessionMessageTable.origin)
						}.reduce { acc, cond -> acc or cond }
					}.forEach { row ->
						val message = SessionMessageTable.readContent(row)
						val remaining = message.origin - agentIds
						if (remaining.isEmpty()) {
							SessionMessageTable.deleteWhere { SessionMessageTable.id eq message.id }
						} else {
							SessionMessageTable.update({ SessionMessageTable.id eq message.id }) {
								it[origin] = remaining.toList()
								SessionMessageTable.fillContent(it, message.withOrigin(remaining))
							}
						}
					}
			}
			SessionDataTable.deleteWhere { SessionDataTable.id inList id }
		}
	}
	
	private fun AgentMessage.withOrigin(newOrigin: Set<UUID>): AgentMessage = when (this) {
		is AgentMessage.User -> copy(origin = newOrigin)
		is AgentMessage.Assistant -> copy(origin = newOrigin)
		is AgentMessage.Tool.Call -> copy(origin = newOrigin)
		is AgentMessage.Tool.Result -> copy(origin = newOrigin)
		is AgentMessage.Compact -> copy(origin = newOrigin)
		is AgentMessage.UsageRecord -> copy(origin = newOrigin)
	}
	
	private fun ResultRow.toSessionData(): SessionData =
		SessionData(
			id = this[SessionDataTable.id],
			title = this[SessionDataTable.title],
			overview = this[SessionDataTable.overview],
			workspaceId = this[SessionDataTable.workspaceId],
			creationTime = this[SessionDataTable.creationTime],
			lastAccessTime = this[SessionDataTable.lastAccessTime],
			agentIndex = SessionDataTable.readAgentIndex(this),
		)
	
	
	override suspend fun saveAgent(agentData: AgentData) {
		db.transaction {
			AgentDataTable.upsert {
				it[id] = agentData.id
				it[name] = agentData.name.value
				it[sessionId] = agentData.sessionId
				it[creationTime] = agentData.creationTime
				it[lastAccessTime] = agentData.lastAccessTime
				AgentDataTable.fillModel(it, agentData.model)
				AgentDataTable.fillContext(it, agentData.context)
				it[activeTools] = agentData.activeTools.toList()
			}
		}
	}
	
	override suspend fun loadAgent(agentId: UUID): AgentData? =
		db.transaction {
			AgentDataTable.selectAll()
				.where { AgentDataTable.id eq agentId }
				.singleOrNull()
				?.toAgentData()
		}
	
	override suspend fun deleteAgent(agentId: UUID) {
		db.transaction {
			AgentDataTable.deleteWhere { AgentDataTable.id eq agentId }
		}
	}
	
	private fun ResultRow.toAgentData(): AgentData =
		AgentData(
			id = this[AgentDataTable.id],
			name = this[AgentDataTable.name].toKebab(),
			sessionId = this[AgentDataTable.sessionId],
			creationTime = this[AgentDataTable.creationTime],
			lastAccessTime = this[AgentDataTable.lastAccessTime],
			model = AgentDataTable.readModel(this),
			context = AgentDataTable.readContext(this),
			activeTools = this[AgentDataTable.activeTools].toSet(),
		)
	
	override suspend fun saveMessages(messages: List<AgentMessage>) {
		db.transaction {
			messages.forEach { msg ->
				SessionMessageTable.upsert {
					it[id] = msg.id
					it[type] = SessionMessageTable.typeOf(msg)
					it[timestamp] = msg.timestamp
					it[origin] = msg.origin.toList()
					SessionMessageTable.fillContent(it, msg)
				}
			}
		}
	}
	
	override suspend fun loadMessages(ids: Set<UUID>): List<AgentMessage> =
		db.transaction {
			SessionMessageTable.selectAll()
				.where { SessionMessageTable.id inList ids }
				.map { it.toSessionMessage() }
		}
	
	override suspend fun deleteMessages(ids: Set<UUID>) {
		db.transaction {
			SessionMessageTable.deleteWhere { SessionMessageTable.id inList ids }
		}
	}
	
	private fun ResultRow.toSessionMessage(): AgentMessage =
		SessionMessageTable.readContent(this)
}
