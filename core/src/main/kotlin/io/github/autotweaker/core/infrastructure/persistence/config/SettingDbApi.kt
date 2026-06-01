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

package io.github.autotweaker.core.infrastructure.persistence.config

import io.github.autotweaker.api.dev.DbAPI
import io.github.autotweaker.api.types.dev.SettingEntry
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class SettingDbApi(private val db: org.jetbrains.exposed.v1.jdbc.Database) : DbAPI<SettingEntry> {
	override suspend fun list(range: UIntRange): List<SettingEntry> = transaction(db) {
		val count = (range.last - range.first + 1u).toInt()
		ConfigTable.selectAll().limit(count).offset(range.first.toLong()).map { it.toSettingEntry() }
	}
	
	override suspend fun get(key: String): SettingEntry? = transaction(db) {
		ConfigTable.selectAll().where { ConfigTable.keyName eq key }
			.firstOrNull()?.toSettingEntry()
	}
	
	override suspend fun put(content: SettingEntry): Unit = transaction(db) {
		ConfigTable.upsert {
			it[keyName] = content.key
			it[valJson] = content.value
			it[description] = content.description
		}
	}
	
	override suspend fun delete(key: String): Unit = transaction(db) {
		ConfigTable.deleteWhere { keyName eq key }
	}
	
	private fun ResultRow.toSettingEntry() = SettingEntry(
		key = this[ConfigTable.keyName],
		value = this[ConfigTable.valJson],
		description = this[ConfigTable.description],
	)
}
