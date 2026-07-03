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

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.Request
import io.github.autotweaker.api.dev.DbAPI
import io.github.autotweaker.api.dev.DbDebugAPI
import io.github.autotweaker.api.types.dev.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

class DebugHandler(
	private val debug: DbDebugAPI,
	private val prompt: suspend (String, Boolean) -> String
) {
	companion object {
		private val TABLES = listOf(
			"setting", "jsonStore", "sessionData",
			"agentData", "sessionMessage", "secrets"
		)
	}
	
	fun handle(request: Request): Flow<CmdOutput> = flow {
		when {
			request.has("list") -> listEntries(request)
			request.has("get") -> getEntry(request)
			request.has("put") -> putEntry(request)
			request.has("delete") -> deleteEntry(request)
		}
	}
	
	private suspend fun FlowCollector<CmdOutput>.listEntries(request: Request) {
		val table = TABLES.first { request.has(it) }
		val (from, to) = request.get("list")?.split("-", limit = 2)?.map { it.trim().toUInt() }
			?: run { emitDone(1); return }
		
		entryApi(table).list(from..to).forEach {
			emit(CmdOutput.Data(it.toString()))
		}
		emitDone()
	}
	
	private suspend fun FlowCollector<CmdOutput>.getEntry(request: Request) {
		val table = TABLES.first { request.has(it) }
		val key = request.get("get") ?: run { emitDone(1); return }
		val entry = entryApi(table).get(key)
		emit(CmdOutput.Data(entry?.toString() ?: run { emitDone(1); return }))
		emitDone()
	}
	
	private suspend fun FlowCollector<CmdOutput>.putEntry(request: Request) {
		val table = TABLES.first { request.has(it) }
		entryApi(table).put(promptEntry(table, request.get("put") ?: run { emitDone(1); return }))
		emitDone()
	}
	
	private suspend fun FlowCollector<CmdOutput>.deleteEntry(request: Request) {
		val table = TABLES.first { request.has(it) }
		val key = request.get("delete") ?: run { emitDone(1); return }
		entryApi(table).delete(key)
		emitDone()
	}
	
	private fun api(table: String): DbAPI<*> = when (table) {
		"setting" -> debug.setting
		"jsonStore" -> debug.jsonStore
		"sessionData" -> debug.sessionData
		"agentData" -> debug.agentData
		"sessionMessage" -> debug.sessionMessage
		"secrets" -> debug.secrets
		else -> error("Unknown table: $table")
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun entryApi(table: String) = api(table) as DbAPI<DbEntry>
	
	private suspend fun promptEntry(table: String, key: String): DbEntry =
		when (table) {
			"setting" -> SettingEntry(
				key,
				prompt("value: ", true),
			)
			
			"jsonStore" -> JsonStoreEntry(
				key,
				prompt("content: ", true)
			)
			
			"sessionData" -> SessionDataEntry(
				key,
				prompt("title: ", true),
				prompt("overview: ", true),
				prompt("model: ", true),
				prompt("workspaceId: ", true),
			)
			
			"agentData" -> AgentDataEntry(
				key,
				prompt("name: ", true),
				prompt("model: ", true),
				prompt("context: ", true),
				prompt("activeTools: ", true)
			)
			
			"sessionMessage" -> SessionMessageEntry(
				key,
				prompt("type: ", true),
				prompt("timestamp: ", true).toLong(),
				prompt("content: ", true)
			)
			
			"secrets" -> SecretEntry(
				key,
				prompt("content: ", true)
			)
			
			else -> error("Unknown table: $table")
		}
}
