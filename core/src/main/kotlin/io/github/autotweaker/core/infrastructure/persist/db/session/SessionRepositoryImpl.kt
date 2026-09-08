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

import io.github.autotweaker.api.orNull
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.AgentData
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.agent.AgentMessageType
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

class SessionRepositoryImpl(store: DatabaseStore) : SessionRepository,
	DbStore(
		store, "Sessions",
		SessionDataTable, AgentDataTable, AgentMessageTable, MessageOwnershipTable
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
					it[agentIndex] = data.agentIndex
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
	
	override suspend fun deleteSessions(id: Set<UUID>) {
		db.transaction {
			val agentIds = AgentDataTable.selectAll()
				.where { AgentDataTable.sessionId inList id }
				.mapTo(mutableSetOf()) { it[AgentDataTable.id] }
			val affected = if (agentIds.isNotEmpty()) MessageOwnershipTable.selectAll()
				.where { MessageOwnershipTable.agentId inList agentIds }
				.mapTo(mutableSetOf()) { it[MessageOwnershipTable.messageId] }
			else emptySet()
			SessionDataTable.deleteWhere { SessionDataTable.id inList id }
			if (affected.isNotEmpty()) {
				val surviving = MessageOwnershipTable.selectAll()
					.where { MessageOwnershipTable.messageId inList affected }
					.mapTo(mutableSetOf()) { it[MessageOwnershipTable.messageId] }
				val orphans = affected - surviving
				if (orphans.isNotEmpty())
					AgentMessageTable.deleteWhere { AgentMessageTable.id inList orphans }
			}
		}
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
					it[searchText] = searchTextOf(msg)
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
	
	private fun typeOf(msg: AgentMessage): AgentMessageType = when (msg) {
		is AgentMessage.User -> AgentMessageType.USER
		is AgentMessage.Assistant -> AgentMessageType.ASSISTANT
		is AgentMessage.Tool.Call -> AgentMessageType.TOOL_CALL
		is AgentMessage.Tool.Result -> AgentMessageType.TOOL_RESULT
		is AgentMessage.Compact -> AgentMessageType.COMPACT
		is AgentMessage.UsageRecord -> AgentMessageType.USAGE_RECORD
	}
	
	private fun searchTextOf(msg: AgentMessage): String? = when (msg) {
		is AgentMessage.User -> msg.content.content?.filterIsInstance<ContentPart.Text>()
			?.joinToString("\n") { it.content }?.ifBlank { null }
		
		is AgentMessage.Assistant -> msg.content?.orNull()
		is AgentMessage.Tool.Call -> msg.arguments
		is AgentMessage.Tool.Result -> msg.content
		is AgentMessage.Compact -> msg.content
		is AgentMessage.UsageRecord -> null
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
