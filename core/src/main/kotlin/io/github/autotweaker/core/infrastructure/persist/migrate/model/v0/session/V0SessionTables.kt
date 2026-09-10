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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

class V0SessionTable(tableName: String) : Table(tableName) {
	val id = javaUUID("id")
	val title = varchar("title", 512).nullable()
	val overview = varchar("overview", 512).nullable()
	val workspaceId = javaUUID("workspace_id")
	val agentIndexJson = text("agent_index_json")
}

class V0AgentTable(tableName: String) : Table(tableName) {
	val id = javaUUID("id")
	val name = varchar("name", 128)
	val modelJson = text("model_json")
	val contextJson = text("context_json")
	val activeToolsJson = text("active_tools_json")
}

class V0MessageTable(tableName: String) : Table(tableName) {
	val id = javaUUID("id")
	val contentJson = text("content_json")
}
