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

import io.github.autotweaker.api.types.agent.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object SessionDataTable : Table("session_data") {
	val id = javaUUID("id")
	val title = varchar("title", 512).nullable()
	val overview = varchar("overview", 512).nullable()
	val workspaceId = javaUUID("workspace_id")
	val creationTime = timestamp("creation_time")
	val lastAccessTime = timestamp("last_access_time")
	val agentIndex = jsonb<AgentIndex>("agent_index", Json)
	
	override val primaryKey = PrimaryKey(id)
}

object AgentDataTable : Table("agent_data") {
	val id = javaUUID("id")
	val name = varchar("name", 128)
	val sessionId = reference("session_id", SessionDataTable.id, onDelete = ReferenceOption.CASCADE)
	val creationTime = timestamp("creation_time")
	val lastAccessTime = timestamp("last_access_time")
	val model = jsonb<ModelConfig>("model", Json)
	val context = jsonb<AgentContext>("context", Json)
	val activeTools = array<String>("active_tools")
	
	override val primaryKey = PrimaryKey(id)
}

object AgentMessageTable : Table("agent_message") {
	val id = javaUUID("id")
	val type = customEnumeration(
		name = "type",
		sql = "ENUM('USER','ASSISTANT','TOOL_CALL','TOOL_RESULT','COMPACT','USAGE_RECORD')",
		fromDb = { value -> AgentMessageType.valueOf(value as String) },
		toDb = { it.name }
	)
	val timestamp = timestamp("timestamp")
	val searchText = text("search_text").nullable()
	val content = jsonb<AgentMessage>("content", Json)
	
	override val primaryKey = PrimaryKey(id)
}

object MessageOwnershipTable : Table("message_ownership") {
	val messageId = reference("message_id", AgentMessageTable.id, onDelete = ReferenceOption.CASCADE)
	val agentId = reference("agent_id", AgentDataTable.id, onDelete = ReferenceOption.CASCADE)
	
	override val primaryKey = PrimaryKey(messageId, agentId)
	
	init {
		index(false, agentId)
	}
}
