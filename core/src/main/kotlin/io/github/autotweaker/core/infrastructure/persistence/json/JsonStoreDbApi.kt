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

import io.github.autotweaker.api.types.dev.JsonStoreEntry
import io.github.autotweaker.core.infrastructure.persistence.store.AbstractDbApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.Database

class JsonStoreDbApi(db: Database) : AbstractDbApi<JsonStoreEntry>(db, JsonStoreTable, JsonStoreTable.namespace) {
	override fun ResultRow.toEntry() = JsonStoreEntry(
		key = this[JsonStoreTable.namespace],
		content = this[JsonStoreTable.content],
	)
	
	override fun UpsertStatement<Long>.fill(content: JsonStoreEntry) {
		this[JsonStoreTable.namespace] = content.key
		this[JsonStoreTable.content] = content.content
	}
}
