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
import io.github.autotweaker.api.types.agent.AgentMessageType
import io.github.autotweaker.api.types.agent.content
import io.github.autotweaker.api.types.session.SessionCursor
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionSort
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.Instant

class SessionRepositoryImpl(store: DatabaseStore) : SessionRepository,
	DbStore(
		store, "Sessions",
		SessionDataTable, AgentDataTable, AgentMessageTable, MessageOwnershipTable
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
		val orphans = db.transaction {
			val agentIds = AgentDataTable.selectAll()
				.where { AgentDataTable.sessionId inList id }
				.mapTo(mutableSetOf()) { it[AgentDataTable.id] }
			val affected = if (agentIds.isNotEmpty()) MessageOwnershipTable.selectAll()
				.where { MessageOwnershipTable.agentId inList agentIds }
				.mapTo(mutableSetOf()) { it[MessageOwnershipTable.messageId] }
			else emptySet()
			SessionDataTable.deleteWhere { SessionDataTable.id inList id }
			if (affected.isEmpty()) return@transaction emptySet()
			val surviving = MessageOwnershipTable.selectAll()
				.where { MessageOwnershipTable.messageId inList affected }
				.mapTo(mutableSetOf()) { it[MessageOwnershipTable.messageId] }
			val orphans = affected - surviving
			if (orphans.isNotEmpty())
				AgentMessageTable.deleteWhere { AgentMessageTable.id inList orphans }
			return@transaction orphans
		}
		MessageSearch.delete(orphans)
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
			val messageIds = messages.map { it.id }
			if (messageIds.isNotEmpty())
				MessageOwnershipTable.deleteWhere { MessageOwnershipTable.messageId inList messageIds }
			messages.forEach { msg ->
				AgentMessageTable.upsert {
					it[id] = msg.id
					it[type] = typeOf(msg)
					it[timestamp] = msg.timestamp
					it[content] = msg
				}
				msg.origin.forEach { owner ->
					MessageOwnershipTable.insert {
						it[messageId] = msg.id
						it[agentId] = owner
					}
				}
			}
		}
		messages.forEach { msg ->
			MessageSearch.upsert(msg.id, typeOf(msg), msg.timestamp, msg.content())
		}
	}
	
	override suspend fun loadMessages(ids: Set<UUID>): List<AgentMessage> =
		db.transaction {
			val origins = MessageOwnershipTable.selectAll()
				.where { MessageOwnershipTable.messageId inList ids }
				.groupBy { it[MessageOwnershipTable.messageId] }
				.mapValues { (_, rows) ->
					rows.mapTo(mutableSetOf()) {
						it[MessageOwnershipTable.agentId]
					}
				}
			AgentMessageTable.selectAll()
				.where { AgentMessageTable.id inList ids }
				.map { row ->
					row[AgentMessageTable.content].withOrigin(origins[row[AgentMessageTable.id]].orEmpty())
				}
		}
	
	override suspend fun searchMessages(
		query: String,
		type: AgentMessageType?,
		from: Instant?,
		to: Instant?,
	): Set<UUID> = MessageSearch.search(query, type, from, to)
	
	private fun typeOf(msg: AgentMessage): AgentMessageType = when (msg) {
		is AgentMessage.User -> AgentMessageType.USER
		is AgentMessage.Assistant -> AgentMessageType.ASSISTANT
		is AgentMessage.Tool.Call -> AgentMessageType.TOOL_CALL
		is AgentMessage.Tool.Result -> AgentMessageType.TOOL_RESULT
		is AgentMessage.Compact -> AgentMessageType.COMPACT
		is AgentMessage.UsageRecord -> AgentMessageType.USAGE_RECORD
	}
	
	private fun AgentMessage.withOrigin(newOrigin: Set<UUID>): AgentMessage = when (this) {
		is AgentMessage.User -> copy(origin = newOrigin)
		is AgentMessage.Assistant -> copy(origin = newOrigin)
		is AgentMessage.Tool.Call -> copy(origin = newOrigin)
		is AgentMessage.Tool.Result -> copy(origin = newOrigin)
		is AgentMessage.Compact -> copy(origin = newOrigin)
		is AgentMessage.UsageRecord -> copy(origin = newOrigin)
	}
}
