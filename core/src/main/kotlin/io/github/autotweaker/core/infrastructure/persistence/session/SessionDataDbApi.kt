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

package io.github.autotweaker.core.infrastructure.persistence.session

import io.github.autotweaker.api.types.dev.SessionDataEntry
import io.github.autotweaker.core.infrastructure.persistence.store.AbstractDbApi
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement

object SessionDataDbApi : AbstractDbApi<SessionDataEntry>() {
	fun init(databaseStore: DatabaseStore) {
		super.init(databaseStore.connect("Sessions"), SessionDataTable, SessionDataTable.id)
	}
	
	override fun ResultRow.toEntry() = SessionDataEntry(
		key = this[SessionDataTable.id],
		title = this[SessionDataTable.title],
		workspaceId = this[SessionDataTable.workspaceId],
		config = this[SessionDataTable.configJson],
	)
	
	override fun UpsertStatement<Long>.fill(content: SessionDataEntry) {
		this[SessionDataTable.id] = content.key
		this[SessionDataTable.title] = content.title
		this[SessionDataTable.workspaceId] = content.workspaceId
		this[SessionDataTable.configJson] = content.config
	}
}
