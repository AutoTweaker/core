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

package io.github.autotweaker.core.data.session

import io.github.autotweaker.api.types.session.SessionData
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import io.github.autotweaker.core.session.SessionStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

class SessionStoreImpl : SessionStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	private var initialized = false
	
	@Synchronized
	fun init() {
		if (initialized) return
		db = H2DatabaseStore.connect("Sessions")
		transaction(db) {
			SchemaUtils.create(SessionDataTable, SessionContextTable, SessionMessageTable)
		}
		initialized = true
		logger.info("SessionStore initialized")
	}
	
	private fun ensureInit() {
		if (!initialized) init()
	}
	
	// region Sessions
	
	override suspend fun saveSessions(sessionData: List<SessionData>) {
		ensureInit()
		transaction(db) {
			sessionData.forEach { data ->
				SessionDataTable.upsert {
					it[id] = data.id.toString()
					it[title] = data.title
					it[workspaceName] = data.workspaceName
					fillConfig(it, data.config)
				}
			}
		}
	}
	
	override suspend fun loadSessions(ids: List<UUID>): List<SessionData>? {
		ensureInit()
		return transaction(db) {
			val idStrings = ids.map { it.toString() }
			val rows = SessionDataTable.selectAll().where { SessionDataTable.id inList idStrings }
			if (rows.empty()) null
			else rows.map { it.toSessionData() }
		}
	}
	
	override suspend fun loadAllSessions(): List<SessionData>? {
		ensureInit()
		return transaction(db) {
			val rows = SessionDataTable.selectAll()
			if (rows.empty()) null
			else rows.map { it.toSessionData() }
		}
	}
	
	override suspend fun deleteSessions(id: List<UUID>) {
		ensureInit()
		val idStrings = id.map { it.toString() }
		transaction(db) {
			SessionDataTable.deleteWhere { SessionDataTable.id inList idStrings }
			SessionContextTable.deleteWhere { SessionContextTable.sessionId inList idStrings }
		}
	}
	
	private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionData(): SessionData {
		return SessionData(
			id = UUID.fromString(this[SessionDataTable.id]),
			title = this[SessionDataTable.title],
			workspaceName = this[SessionDataTable.workspaceName],
			config = SessionDataTable.readConfig(this),
		)
	}
	
	// endregion
	
	// region Context
	
	override suspend fun saveContext(sessionId: UUID, context: SessionContext) {
		ensureInit()
		transaction(db) {
			SessionContextTable.upsert {
				it[SessionContextTable.sessionId] = sessionId.toString()
				it[systemPrompt] = context.systemPrompt
				fillUsage(it, context.usage)
				fillIndex(it, context.index)
				fillDroppedMessages(it, context.droppedMessages)
			}
		}
	}
	
	override suspend fun loadContext(sessionId: UUID): SessionContext? {
		ensureInit()
		return transaction(db) {
			SessionContextTable.selectAll().where { SessionContextTable.sessionId eq sessionId.toString() }
				.singleOrNull()?.let { row ->
					SessionContext(
						systemPrompt = row[SessionContextTable.systemPrompt],
						usage = SessionContextTable.readUsage(row),
						index = SessionContextTable.readIndex(row),
						droppedMessages = SessionContextTable.readDroppedMessages(row),
					)
				}
		}
	}
	
	override suspend fun deleteContext(sessionId: UUID) {
		ensureInit()
		transaction(db) {
			SessionContextTable.deleteWhere { SessionContextTable.sessionId eq sessionId.toString() }
		}
	}
	
	// endregion
	
	// region Messages
	
	override suspend fun saveMessages(messages: List<SessionMessage>) {
		ensureInit()
		transaction(db) {
			messages.forEach { msg ->
				SessionMessageTable.upsert {
					it[id] = msg.id.toString()
					it[type] = typeOf(msg)
					it[timestamp] = msg.timestamp.toEpochMilliseconds()
					fillContent(it, msg)
				}
			}
		}
	}
	
	override suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>? {
		ensureInit()
		return transaction(db) {
			val idStrings = ids.map { it.toString() }
			val rows = SessionMessageTable.selectAll().where { SessionMessageTable.id inList idStrings }
			if (rows.empty()) null
			else rows.map { it.toSessionMessage() }
		}
	}
	
	override suspend fun deleteMessages(ids: List<UUID>) {
		ensureInit()
		val idStrings = ids.map { it.toString() }
		transaction(db) {
			SessionMessageTable.deleteWhere { SessionMessageTable.id inList idStrings }
		}
	}
	
	private fun org.jetbrains.exposed.v1.core.ResultRow.toSessionMessage(): SessionMessage {
		return SessionMessageTable.readContent(this)
	}
	
	// endregion
}
