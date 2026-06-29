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

import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.session.ModelConfig
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

private val sessionJson = Json {
	ignoreUnknownKeys = true
	serializersModule = SerializersModule {
		polymorphic(SessionMessage::class) {
			subclass(SessionMessage.User::class)
			subclass(SessionMessage.Assistant::class)
			subclass(SessionMessage.Tool.Call::class)
			subclass(SessionMessage.Tool.Result::class)
			subclass(SessionMessage.Compact::class)
			subclass(SessionMessage.UsageRecord::class)
		}
	}
}

private inline fun <reified T> fillJson(it: UpdateBuilder<*>, column: Column<String>, value: T) {
	it[column] = sessionJson.encodeToString(serializer<T>(), value)
}

private inline fun <reified T> readJson(row: ResultRow, column: Column<String>): T =
	sessionJson.decodeFromString(serializer<T>(), row[column])

object SessionDataTable : Table("session_data") {
	val id = varchar("id", 36)
	val title = varchar("title", 512).nullable()
	val overview = varchar("overview", 512).nullable()
	val workspaceId = varchar("workspace_id", 36)
	val agentIndexJson = text("agent_index_json")
	
	override val primaryKey = PrimaryKey(id)
	
	fun fillAgentIndex(it: UpdateBuilder<*>, index: AgentIndex) = fillJson(it, agentIndexJson, index)
	fun readAgentIndex(row: ResultRow): AgentIndex = readJson(row, agentIndexJson)
}

object AgentDataTable : Table("agent_data") {
	val id = varchar("id", 36)
	val name = varchar("name", 128)
	val modelJson = text("model_json")
	val contextJson = text("context_json")
	val activeToolsJson = text("active_tools_json")
	
	override val primaryKey = PrimaryKey(id)
	
	fun fillModel(it: UpdateBuilder<*>, model: ModelConfig) = fillJson(it, modelJson, model)
	fun readModel(row: ResultRow): ModelConfig = readJson(row, modelJson)
	fun fillContext(it: UpdateBuilder<*>, context: SessionContext) = fillJson(it, contextJson, context)
	fun readContext(row: ResultRow): SessionContext = readJson(row, contextJson)
	fun fillActiveTools(it: UpdateBuilder<*>, tools: List<String>) = fillJson(it, activeToolsJson, tools)
	
	fun readActiveTools(row: ResultRow): List<String> {
		val jsonStr = row[activeToolsJson]
		if (jsonStr.isBlank()) return emptyList()
		return readJson(row, activeToolsJson)
	}
}

object SessionMessageTable : Table("session_message") {
	val id = varchar("id", 36)
	val type = varchar("type", 32)
	val timestamp = long("timestamp")
	val contentJson = text("content_json")
	
	override val primaryKey = PrimaryKey(id)
	
	fun fillContent(it: UpdateBuilder<*>, msg: SessionMessage) = fillJson(it, contentJson, msg)
	fun readContent(row: ResultRow): SessionMessage = readJson(row, contentJson)
	
	fun typeOf(msg: SessionMessage): String = when (msg) {
		is SessionMessage.User -> "USER"
		is SessionMessage.Assistant -> "ASSISTANT"
		is SessionMessage.Tool.Call -> "TOOL_CALL"
		is SessionMessage.Tool.Result -> "TOOL_RESULT"
		is SessionMessage.Compact -> "COMPACT"
		is SessionMessage.UsageRecord -> "USAGE_RECORD"
	}
}
