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

package io.github.autotweaker.core.infrastructure.persistence.session

import io.github.autotweaker.api.types.KebabId.Companion.toKebabId
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log

object SessionRepositoryImpl : SessionRepository, Loggable {
	private lateinit var db: Database
	
	fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("Sessions")
		transaction(db) {
			SchemaUtils.create(SessionDataTable, AgentDataTable, SessionMessageTable)
		}
		log.info("Initialized SessionRepository")
	}
	
	// region Sessions
	
	override suspend fun saveSessions(sessionData: List<SessionData>) {
		transaction(db) {
			sessionData.forEach { data ->
				SessionDataTable.upsert {
					it[id] = data.id.toString()
					it[title] = data.title
					it[overview] = data.overview
					it[workspaceId] = data.workspaceId.toString()
					SessionDataTable.fillModel(it, data.model)
					SessionDataTable.fillAgentIndex(it, data.agentIndex)
				}
			}
		}
	}
	
	override suspend fun loadSessions(ids: List<UUID>): List<SessionData> {
		return transaction(db) {
			val idStrings = ids.map { it.toString() }
			SessionDataTable.selectAll()
				.where { SessionDataTable.id inList idStrings }
				.map { it.toSessionData() }
		}
	}
	
	override suspend fun loadAllSessions(): List<SessionData> {
		return transaction(db) {
			SessionDataTable.selectAll()
				.map { it.toSessionData() }
		}
	}
	
	override suspend fun deleteSessions(id: List<UUID>) {
		val idStrings = id.map { it.toString() }
		transaction(db) {
			SessionDataTable.deleteWhere { SessionDataTable.id inList idStrings }
		}
	}
	
	private fun ResultRow.toSessionData(): SessionData {
		return SessionData(
			id = UUID.fromString(this[SessionDataTable.id]),
			title = this[SessionDataTable.title],
			overview = this[SessionDataTable.overview],
			model = SessionDataTable.readModel(this),
			workspaceId = UUID.fromString(this[SessionDataTable.workspaceId]),
			agentIndex = SessionDataTable.readAgentIndex(this),
		)
	}
	
	// endregion
	
	// region Agent
	
	override suspend fun saveAgent(agentData: AgentData) {
		transaction(db) {
			AgentDataTable.upsert {
				it[id] = agentData.id.toString()
				it[name] = agentData.name.value
				AgentDataTable.fillModel(it, agentData.model)
				AgentDataTable.fillContext(it, agentData.context)
				AgentDataTable.fillActiveTools(it, agentData.activeTools)
			}
		}
	}
	
	override suspend fun loadAgent(agentId: UUID): AgentData? {
		return transaction(db) {
			AgentDataTable.selectAll()
				.where { AgentDataTable.id eq agentId.toString() }
				.singleOrNull()
				?.toAgentData()
		}
	}
	
	override suspend fun deleteAgent(agentId: UUID) {
		transaction(db) {
			AgentDataTable.deleteWhere { AgentDataTable.id eq agentId.toString() }
		}
	}
	
	private fun ResultRow.toAgentData(): AgentData {
		return AgentData(
			id = UUID.fromString(this[AgentDataTable.id]),
			name = this[AgentDataTable.name].toKebabId(),
			model = AgentDataTable.readModel(this),
			context = AgentDataTable.readContext(this),
			activeTools = AgentDataTable.readActiveTools(this),
		)
	}
	
	// endregion
	
	// region Messages
	
	override suspend fun saveMessages(messages: List<SessionMessage>) {
		transaction(db) {
			messages.forEach { msg ->
				SessionMessageTable.upsert {
					it[id] = msg.id.toString()
					it[type] = SessionMessageTable.typeOf(msg)
					it[timestamp] = msg.timestamp.toEpochMilliseconds()
					SessionMessageTable.fillContent(it, msg)
				}
			}
		}
	}
	
	override suspend fun loadMessages(ids: List<UUID>): List<SessionMessage> {
		return transaction(db) {
			val idStrings = ids.map { it.toString() }
			SessionMessageTable.selectAll()
				.where { SessionMessageTable.id inList idStrings }
				.map { it.toSessionMessage() }
		}
	}
	
	override suspend fun deleteMessages(ids: List<UUID>) {
		val idStrings = ids.map { it.toString() }
		transaction(db) {
			SessionMessageTable.deleteWhere { SessionMessageTable.id inList idStrings }
		}
	}
	
	private fun ResultRow.toSessionMessage(): SessionMessage {
		return SessionMessageTable.readContent(this)
	}
	
	// endregion
}