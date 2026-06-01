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

package io.github.autotweaker.core.infrastructure.persistence.json

import io.github.autotweaker.api.dev.DbAPI
import io.github.autotweaker.api.types.dev.JsonStoreEntry
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class JsonStoreDbApi(private val db: Database) : DbAPI<JsonStoreEntry> {
	override suspend fun list(range: UIntRange): List<JsonStoreEntry> = transaction(db) {
		val count = (range.last - range.first + 1u).toInt()
		JsonStoreTable.selectAll().limit(count).offset(range.first.toLong()).map { it.toJsonStoreEntry() }
	}
	
	override suspend fun get(key: String): JsonStoreEntry? = transaction(db) {
		JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq key }
			.firstOrNull()?.toJsonStoreEntry()
	}
	
	override suspend fun put(content: JsonStoreEntry): Unit = transaction(db) {
		JsonStoreTable.upsert {
			it[namespace] = content.key
			it[JsonStoreTable.content] = content.content
		}
	}
	
	override suspend fun delete(key: String): Unit = transaction(db) {
		JsonStoreTable.deleteWhere { namespace eq key }
	}
	
	private fun ResultRow.toJsonStoreEntry() = JsonStoreEntry(
		key = this[JsonStoreTable.namespace],
		content = this[JsonStoreTable.content],
	)
}
