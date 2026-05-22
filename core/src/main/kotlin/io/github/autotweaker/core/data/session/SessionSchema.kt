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

import org.jetbrains.exposed.v1.core.Table

object SessionDataTable : Table("session_data") {
	val id = varchar("id", 36)
	val title = varchar("title", 512).nullable()
	val workspaceId = varchar("workspace_id", 36)
	val configJson = text("config_json")
	
	override val primaryKey = PrimaryKey(id)
}

object SessionContextTable : Table("session_context") {
	val sessionId = varchar("session_id", 36)
	val systemPrompt = text("system_prompt")
	val indexJson = text("index_json")
	val droppedMessagesJson = text("dropped_messages_json")
	
	override val primaryKey = PrimaryKey(sessionId)
}

object SessionMessageTable : Table("session_message") {
	val id = varchar("id", 36)
	val type = varchar("type", 32)
	val timestamp = long("timestamp")
	val contentJson = text("content_json")
	
	override val primaryKey = PrimaryKey(id)
}
