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

package io.github.autotweaker.core.infrastructure.persist.db.usage

import io.github.autotweaker.api.types.debug.UsageEntry
import io.github.autotweaker.core.infrastructure.persist.store.AbstractDbApi
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import java.util.*

object UsageDbApi : AbstractDbApi<UsageEntry, UUID>() {
	fun init(databaseStore: DatabaseStore) {
		super.init(databaseStore.connect("Usages"), UsageTable, UsageTable.id)
	}
	
	override fun ResultRow.toEntry() = UsageEntry(
		key = this[UsageTable.id],
		modelId = this[UsageTable.modelId],
		timestamp = this[UsageTable.timestamp],
		promptTokens = this[UsageTable.promptTokens],
		completionTokens = this[UsageTable.completionTokens],
		reasoningTokens = this[UsageTable.reasoningTokens],
		cacheHitTokens = this[UsageTable.cacheHitTokens],
		imageTokens = this[UsageTable.imageTokens],
	)
	
	override fun UpsertStatement<Long>.fill(content: UsageEntry) {
		this[UsageTable.id] = content.key
		this[UsageTable.modelId] = content.modelId
		this[UsageTable.timestamp] = content.timestamp
		this[UsageTable.promptTokens] = content.promptTokens
		this[UsageTable.completionTokens] = content.completionTokens
		this[UsageTable.reasoningTokens] = content.reasoningTokens
		this[UsageTable.cacheHitTokens] = content.cacheHitTokens
		this[UsageTable.imageTokens] = content.imageTokens
	}
}
