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

import io.github.autotweaker.api.types.debug.SessionMessageEntry
import io.github.autotweaker.core.infrastructure.persist.store.AbstractDbApi
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement

object SessionMessageDbApi : AbstractDbApi<SessionMessageEntry>() {
	fun init(databaseStore: DatabaseStore) {
		super.init(databaseStore.connect("Sessions"), SessionMessageTable, SessionMessageTable.id)
	}
	
	override fun ResultRow.toEntry() = SessionMessageEntry(
		key = this[SessionMessageTable.id],
		type = this[SessionMessageTable.type],
		timestamp = this[SessionMessageTable.timestamp],
		content = this[SessionMessageTable.contentJson],
	)
	
	override fun UpsertStatement<Long>.fill(content: SessionMessageEntry) {
		this[SessionMessageTable.id] = content.key
		this[SessionMessageTable.type] = content.type
		this[SessionMessageTable.timestamp] = content.timestamp
		this[SessionMessageTable.contentJson] = content.content
	}
}
