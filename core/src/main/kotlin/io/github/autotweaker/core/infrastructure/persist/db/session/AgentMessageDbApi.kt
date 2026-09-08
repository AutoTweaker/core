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

import io.github.autotweaker.api.types.debug.AgentMessageEntry
import io.github.autotweaker.core.infrastructure.persist.db.base.AbstractDbApi
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*

class AgentMessageDbApi(private val store: DatabaseStore) : AbstractDbApi<AgentMessageEntry, UUID>(
	AgentMessageTable, AgentMessageTable.id
) {
	override fun connect(): Database = store.connect("Sessions")
	
	override fun ResultRow.toEntry() = AgentMessageEntry(
		key = this[AgentMessageTable.id],
		type = this[AgentMessageTable.type],
		timestamp = this[AgentMessageTable.timestamp],
		content = this[AgentMessageTable.content],
	)
	
	override fun UpsertStatement<Long>.fill(content: AgentMessageEntry) {
		this[AgentMessageTable.id] = content.key
		this[AgentMessageTable.type] = content.type
		this[AgentMessageTable.timestamp] = content.timestamp
		this[AgentMessageTable.content] = content.content
	}
}
