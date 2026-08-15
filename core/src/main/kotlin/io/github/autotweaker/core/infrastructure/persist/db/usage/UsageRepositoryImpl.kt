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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageCursor
import io.github.autotweaker.api.types.llm.UsageEntry
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.infrastructure.persist.db.transaction
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.*
import kotlin.time.Instant

object UsageRepositoryImpl : UsageRepository, Loggable {
	private lateinit var db: Database
	
	suspend fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("Usages")
		db.transaction {
			SchemaUtils.create(UsageTable)
		}
		log.info("Initialized UsageRepository")
	}
	
	override suspend fun save(usages: List<UsageEntry>) {
		db.transaction {
			usages.forEach { entry ->
				UsageTable.upsert {
					it[id] = entry.id
					it[modelId] = entry.modelId
					it[timestamp] = entry.timestamp
					it[promptTokens] = entry.usage.promptTokens
					it[completionTokens] = entry.usage.completionTokens
					it[reasoningTokens] = entry.usage.reasoningTokens
					it[cacheHitTokens] = entry.usage.cacheHitTokens
					it[imageTokens] = entry.usage.imageTokens
				}
			}
		}
	}
	
	override suspend fun load(limit: Int, before: UsageCursor?): List<UsageEntry> =
		db.transaction {
			UsageTable.selectAll()
				.where {
					if (before == null) Op.TRUE
					else (UsageTable.timestamp less before.timestamp) or
							((UsageTable.timestamp eq before.timestamp) and (UsageTable.id less before.id))
				}.orderBy(UsageTable.timestamp to SortOrder.DESC, UsageTable.id to SortOrder.DESC)
				.limit(limit)
				.map { it.toUsageEntry() }
		}
	
	override suspend fun summarize(modelId: UUID?, from: Instant?, to: Instant?): Usage =
		db.transaction {
			val prompt = UsageTable.promptTokens.sum()
			val completion = UsageTable.completionTokens.sum()
			val reasoning = UsageTable.reasoningTokens.sum()
			val cacheHit = UsageTable.cacheHitTokens.sum()
			val image = UsageTable.imageTokens.sum()
			val row = UsageTable.select(prompt, completion, reasoning, cacheHit, image)
				.where {
					buildList {
						modelId?.let { add(UsageTable.modelId eq it) }
						from?.let { add(UsageTable.timestamp greaterEq it) }
						to?.let { add(UsageTable.timestamp lessEq it) }
					}.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
				}.single()
			Usage(
				promptTokens = row[prompt] ?: 0,
				completionTokens = row[completion] ?: 0,
				reasoningTokens = row[reasoning],
				cacheHitTokens = row[cacheHit],
				imageTokens = row[image],
			)
		}
	
	private fun ResultRow.toUsageEntry(): UsageEntry = UsageEntry(
		id = this[UsageTable.id],
		modelId = this[UsageTable.modelId],
		timestamp = this[UsageTable.timestamp],
		usage = Usage(
			promptTokens = this[UsageTable.promptTokens],
			completionTokens = this[UsageTable.completionTokens],
			reasoningTokens = this[UsageTable.reasoningTokens],
			cacheHitTokens = this[UsageTable.cacheHitTokens],
			imageTokens = this[UsageTable.imageTokens],
		),
	)
}
