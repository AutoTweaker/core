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

package io.github.autotweaker.core.infrastructure.persist.db.base

import io.github.autotweaker.api.debug.DbAPI
import io.github.autotweaker.api.types.debug.DbEntry
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

abstract class AbstractDbApi<Entry : DbEntry<PK>, PK>(
	private val table: Table,
	private val pkColumn: Column<PK>
) : DbAPI<Entry, PK> {
	private val db: Database by lazy { connect() }
	
	abstract fun connect(): Database
	abstract fun ResultRow.toEntry(): Entry
	abstract fun UpsertStatement<Long>.fill(content: Entry)
	
	override suspend fun put(content: Entry): Unit = db.transaction {
		table.upsert { it.fill(content) }
	}
	
	override suspend fun list(range: UIntRange): List<Entry> = db.transaction {
		val count = (range.last - range.first + 1u).toInt()
		table.selectAll().limit(count).offset(range.first.toLong()).map { it.toEntry() }
	}
	
	override suspend fun get(key: PK): Entry? = db.transaction {
		table.selectAll().where { pkColumn eq key }.firstOrNull()?.toEntry()
	}
	
	override suspend fun delete(key: PK): Unit = db.transaction {
		table.deleteWhere { pkColumn eq key }
	}
}
