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
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.AgentMessageType
import io.github.autotweaker.api.types.message.type
import io.github.autotweaker.api.types.session.SessionCursor
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionSort
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.Instant

class SessionRepositoryImpl(store: DatabaseStore) : SessionRepository,
	DbStore(
		store, "Sessions",
		SessionDataTable, AgentDataTable, AgentMessageTable
	) {
	
	private val SessionSort.column: Column<Instant>
		get() = when (this) {
			SessionSort.CREATION_TIME -> SessionDataTable.creationTime
			SessionSort.LAST_ACCESS_TIME -> SessionDataTable.lastAccessTime
		}
	
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
					it[agentIndex] = data.agentIndex
				}
			}
		}
	}
	
	override suspend fun loadSession(id: UUID): SessionData? =
		db.transaction {
			SessionDataTable.selectAll()
				.where { SessionDataTable.id eq id }
				.singleOrNull()
				?.toSessionData()
		}
	
	override suspend fun loadSessions(
		workspaceId: UUID?,
		sortBy: SessionSort,
		limit: Int,
		before: SessionCursor?,
	): List<SessionData> = db.transaction {
		val sortColumn = sortBy.column
		SessionDataTable.selectAll()
			.where {
				val scope = workspaceId?.let { SessionDataTable.workspaceId eq it } ?: Op.TRUE
				val page = before?.let {
					(sortColumn less it.time) or
							((sortColumn eq it.time) and (SessionDataTable.id less it.id))
				} ?: Op.TRUE
				scope and page
			}
			.orderBy(sortColumn to SortOrder.DESC, SessionDataTable.id to SortOrder.DESC)
			.limit(limit)
			.map { it.toSessionData() }
	}
	
	override suspend fun deleteSessions(id: Set<UUID>) {
		val removed = db.transaction {
			val agentIds = AgentDataTable.selectAll()
				.where { AgentDataTable.sessionId inList id }
				.mapTo(mutableSetOf()) { it[AgentDataTable.id] }
			SessionDataTable.deleteWhere { SessionDataTable.id inList id }
			if (agentIds.isEmpty()) return@transaction emptySet()
			val removed = AgentMessageTable.selectAll()
				.where { AgentMessageTable.origin inList agentIds }
				.mapTo(mutableSetOf()) { it[AgentMessageTable.id] }
			if (removed.isNotEmpty())
				AgentMessageTable.deleteWhere { AgentMessageTable.id inList removed }
			return@transaction removed
		}
		MessageSearch.delete(removed)
	}
	
	private fun ResultRow.toSessionData(): SessionData =
		SessionData(
			id = this[SessionDataTable.id],
			title = this[SessionDataTable.title],
			overview = this[SessionDataTable.overview],
			workspaceId = this[SessionDataTable.workspaceId],
			creationTime = this[SessionDataTable.creationTime],
			lastAccessTime = this[SessionDataTable.lastAccessTime],
			agentIndex = this[SessionDataTable.agentIndex],
		)
	
	
	override suspend fun saveAgent(agentData: AgentData) {
		db.transaction {
			AgentDataTable.upsert {
				it[id] = agentData.id
				it[name] = agentData.name.value
				it[sessionId] = agentData.sessionId
				it[creationTime] = agentData.creationTime
				it[lastAccessTime] = agentData.lastAccessTime
				it[model] = agentData.model
				it[context] = agentData.context
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
	
	private fun ResultRow.toAgentData(): AgentData =
		AgentData(
			id = this[AgentDataTable.id],
			name = this[AgentDataTable.name].toKebab(),
			sessionId = this[AgentDataTable.sessionId],
			creationTime = this[AgentDataTable.creationTime],
			lastAccessTime = this[AgentDataTable.lastAccessTime],
			model = this[AgentDataTable.model],
			context = this[AgentDataTable.context],
			activeTools = this[AgentDataTable.activeTools].toSet(),
		)
	
	override suspend fun saveMessages(messages: List<AgentMessage>) {
		db.transaction {
			messages.forEach { msg ->
				AgentMessageTable.upsert {
					it[id] = msg.id
					it[type] = msg.type()
					it[timestamp] = msg.timestamp
					it[origin] = msg.origin
					it[content] = msg
				}
			}
		}
		messages.forEach { msg ->
			MessageSearch.upsert(msg.id, msg.type(), msg.timestamp, msg.content())
		}
	}
	
	override suspend fun loadMessages(ids: Set<UUID>): List<AgentMessage> =
		db.transaction {
			AgentMessageTable.selectAll()
				.where { AgentMessageTable.id inList ids }
				.map { it[AgentMessageTable.content] }
		}
	
	override suspend fun searchMessages(
		query: String,
		type: AgentMessageType?,
		from: Instant?,
		to: Instant?,
	): Set<UUID> = MessageSearch.search(query, type, from, to)
	
	override fun loadMessage(id: UUID): AgentMessage? =
		transaction(db) {
			AgentMessageTable.selectAll()
				.where { AgentMessageTable.id eq id }
				.singleOrNull()
				?.get(AgentMessageTable.content)
		}
}
