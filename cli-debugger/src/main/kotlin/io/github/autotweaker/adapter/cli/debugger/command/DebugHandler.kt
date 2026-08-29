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

package io.github.autotweaker.adapter.cli.debugger.command

import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.debug.DbAPI
import io.github.autotweaker.api.debug.DbDebugAPI
import io.github.autotweaker.api.types.debug.*
import java.util.*
import kotlin.time.Instant

class DebugHandler(private val debug: DbDebugAPI) {
	companion object {
		private val TABLES = listOf(
			"setting", "jsonStore", "sessionData",
			"agentData", "sessionMessage", "usage", "secrets"
		)
		private val UUID_TABLES = setOf("sessionData", "agentData", "sessionMessage", "usage", "secrets")
	}
	
	suspend fun Console.handle(): Nothing {
		handleValue("list") { listEntries(it) }
		handleValue("get") { getEntry(it) }
		handleValue("put") { putEntry(it) }
		handleValue("delete") { deleteEntry(it) }
		done(1)
	}
	
	private suspend fun Console.listEntries(range: String) {
		val table = table()
		val (from, to) = range.split("-", limit = 2).map { it.trim().toUInt() }
		entryApi(table).list(from..to).forEach { out(it.toString()) }
	}
	
	private suspend fun Console.getEntry(key: String) {
		val table = table()
		val entry = entryApi(table).get(keyOf(table, key)) ?: error("Entry not found: $key")
		out(entry.toString())
	}
	
	private suspend fun Console.putEntry(key: String) {
		val table = table()
		entryApi(table).put(promptEntry(table, key))
	}
	
	private suspend fun Console.deleteEntry(key: String) {
		val table = table()
		entryApi(table).delete(keyOf(table, key))
	}
	
	private fun keyOf(table: String, key: String): Any =
		if (table in UUID_TABLES) UUID.fromString(key) else key
	
	private suspend fun Console.table() = TABLES.first { hasArg(it) }
	
	private fun api(table: String): DbAPI<*, *> = when (table) {
		"setting" -> debug.setting
		"jsonStore" -> debug.jsonStore
		"sessionData" -> debug.sessionData
		"agentData" -> debug.agentData
		"sessionMessage" -> debug.sessionMessage
		"usage" -> debug.usage
		"secrets" -> debug.secrets
		else -> error("Unknown table: $table")
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun entryApi(table: String) = api(table) as DbAPI<DbEntry<Any>, Any>
	
	private suspend fun Console.promptEntry(table: String, key: String): DbEntry<Any> =
		when (table) {
			"setting" -> SettingEntry(
				key,
				promptOrStdin("value:"),
			)
			
			"jsonStore" -> JsonStoreEntry(
				key,
				promptOrStdin("content:")
			)
			
			"sessionData" -> SessionDataEntry(
				UUID.fromString(key),
				promptOrStdin("title:"),
				promptOrStdin("overview:"),
				UUID.fromString(promptOrStdin("workspaceId:")),
				promptOrStdin("agentIndex:"),
			)
			
			"agentData" -> AgentDataEntry(
				UUID.fromString(key),
				promptOrStdin("name:"),
				promptOrStdin("model:"),
				promptOrStdin("context:"),
				promptOrStdin("activeTools:")
			)
			
			"sessionMessage" -> SessionMessageEntry(
				UUID.fromString(key),
				promptOrStdin("type:"),
				promptOrStdin("timestamp:").toLong(),
				promptOrStdin("content:")
			)
			
			"usage" -> UsageEntry(
				UUID.fromString(key),
				UUID.fromString(promptOrStdin("modelId:")),
				Instant.parse(promptOrStdin("timestamp:")),
				promptOrStdin("promptTokens:").toInt(),
				promptOrStdin("completionTokens:").toInt(),
				promptOrStdin("reasoningTokens:").toIntOrNull(),
				promptOrStdin("cacheHitTokens:").toIntOrNull(),
			)
			
			"secrets" -> SecretEntry(
				UUID.fromString(key),
				promptOrStdin("content:")
			)
			
			else -> error("Unknown table: $table")
		}
}
