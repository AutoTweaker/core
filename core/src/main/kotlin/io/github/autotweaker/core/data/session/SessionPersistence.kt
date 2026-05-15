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

import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.core.llm.Usage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.util.*

val sessionJson = Json {
	ignoreUnknownKeys = true
	serializersModule = SerializersModule {
		polymorphic(SessionMessage::class) {
			subclass(SessionMessage.User::class)
			subclass(SessionMessage.Assistant::class)
			subclass(SessionMessage.Tool.Call::class)
			subclass(SessionMessage.Tool.Result::class)
			subclass(SessionMessage.Compact::class)
		}
	}
}

@Serializable
data class UsageEntry(
	@Serializable(with = UuidSerializer::class)
	val id: UUID,
	val usage: Usage
)

// region SessionData column helpers

fun SessionDataTable.fillConfig(it: UpdateBuilder<*>, config: SessionConfig) {
	it[configJson] = sessionJson.encodeToString(SessionConfig.serializer(), config)
}

fun SessionDataTable.readConfig(row: ResultRow): SessionConfig {
	return sessionJson.decodeFromString(SessionConfig.serializer(), row[configJson])
}

// endregion

// region SessionContext column helpers

fun SessionContextTable.fillIndex(it: UpdateBuilder<*>, index: SessionContextIndex) {
	it[indexJson] = sessionJson.encodeToString(SessionContextIndex.serializer(), index)
}

fun SessionContextTable.readIndex(row: ResultRow): SessionContextIndex {
	return sessionJson.decodeFromString(SessionContextIndex.serializer(), row[indexJson])
}

fun SessionContextTable.fillUsage(it: UpdateBuilder<*>, usage: Map<UUID, Usage>) {
	val list = usage.map { (k, v) -> UsageEntry(k, v) }
	it[usageJson] = sessionJson.encodeToString(ListSerializer(UsageEntry.serializer()), list)
}

fun SessionContextTable.readUsage(row: ResultRow): Map<UUID, Usage> {
	val jsonStr = row[usageJson]
	if (jsonStr.isBlank()) return emptyMap()
	val list = sessionJson.decodeFromString(ListSerializer(UsageEntry.serializer()), jsonStr)
	return list.associate { it.id to it.usage }
}

fun SessionContextTable.fillDroppedMessages(it: UpdateBuilder<*>, dropped: List<UUID>?) {
	it[droppedMessagesJson] = dropped?.let {
		sessionJson.encodeToString(ListSerializer(UuidSerializer), it)
	} ?: ""
}

fun SessionContextTable.readDroppedMessages(row: ResultRow): List<UUID>? {
	val jsonStr = row[droppedMessagesJson]
	if (jsonStr.isBlank()) return null
	return sessionJson.decodeFromString(ListSerializer(UuidSerializer), jsonStr)
}

// endregion

// region SessionMessage column helpers

fun SessionMessageTable.fillContent(it: UpdateBuilder<*>, msg: SessionMessage) {
	it[contentJson] = sessionJson.encodeToString(SessionMessage.serializer(), msg)
}

fun SessionMessageTable.readContent(row: ResultRow): SessionMessage {
	return sessionJson.decodeFromString(SessionMessage.serializer(), row[contentJson])
}

fun typeOf(msg: SessionMessage): String = when (msg) {
	is SessionMessage.User -> "USER"
	is SessionMessage.Assistant -> "ASSISTANT"
	is SessionMessage.Tool.Call -> "TOOL_CALL"
	is SessionMessage.Tool.Result -> "TOOL_RESULT"
	is SessionMessage.Compact -> "COMPACT"
}

// endregion
