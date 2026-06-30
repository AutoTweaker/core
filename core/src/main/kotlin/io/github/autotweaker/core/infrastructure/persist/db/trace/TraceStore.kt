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

package io.github.autotweaker.core.infrastructure.persist.db.trace

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.db.transaction
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object TraceStore : Loggable {
	private lateinit var db: Database
	
	suspend fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("Traces")
		db.transaction { SchemaUtils.create(TraceTable) }
		log.info("Initialized TraceStore  table=traces")
	}
	
	suspend fun insert(origin: String, namespace: String, content: String) {
		db.transaction {
			TraceTable.insert {
				it[TraceTable.origin] = origin
				it[TraceTable.namespace] = namespace
				it[TraceTable.timestamp] = Clock.System.now()
				it[TraceTable.content] = content
			}
		}
	}
	
	suspend fun select(origin: String, namespace: String, timestamp: Instant): String? = db.transaction {
		TraceTable.selectAll().where {
			(TraceTable.origin eq origin) and
					(TraceTable.namespace eq namespace) and
					(TraceTable.timestamp eq timestamp)
		}.firstOrNull()?.get(TraceTable.content)
	}
	
	suspend fun selectOrigins(): List<String> = db.transaction {
		TraceTable.select(TraceTable.origin).withDistinct().map { it[TraceTable.origin] }
	}
	
	suspend fun selectNamespaces(origin: String): List<String> = db.transaction {
		TraceTable.select(TraceTable.namespace)
			.where { TraceTable.origin eq origin }
			.withDistinct()
			.map { it[TraceTable.namespace] }
	}
	
	suspend fun count(origin: String, namespace: String): Int = db.transaction {
		TraceTable.selectAll()
			.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
			.count().toInt()
	}
	
	suspend fun selectEntries(origin: String, namespace: String, range: UIntRange): List<Instant> = db.transaction {
		val count = (range.last - range.first + 1u).toInt()
		TraceTable.select(TraceTable.timestamp)
			.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
			.orderBy(TraceTable.timestamp)
			.limit(count).offset(range.first.toLong())
			.map { it[TraceTable.timestamp] }
	}
	
	suspend fun delete(origin: String, namespace: String, timestamp: Instant): Boolean = db.transaction {
		TraceTable.deleteWhere {
			(TraceTable.origin eq origin) and
					(TraceTable.namespace eq namespace) and
					(TraceTable.timestamp eq timestamp)
		} >= 1
	}
	
	suspend fun deleteByAge(maxAgeDays: Int): Int = db.transaction {
		val cutoff = Clock.System.now() - maxAgeDays.days
		TraceTable.deleteWhere { TraceTable.timestamp less cutoff }
	}
	
	suspend fun trimPerNamespace(maxEntries: Int): Int = db.transaction {
		var totalDeleted = 0
		val origins = TraceTable.select(TraceTable.origin, TraceTable.namespace)
			.withDistinct()
			.groupBy({ it[TraceTable.origin] }, { it[TraceTable.namespace] })
		
		origins.forEach { (origin, namespaces) ->
			namespaces.forEach { namespace ->
				val nsCount = TraceTable.selectAll()
					.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
					.count()
				if (nsCount <= maxEntries.toLong()) return@forEach
				
				val cutoffTimestamp = TraceTable.select(TraceTable.timestamp)
					.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
					.orderBy(TraceTable.timestamp, SortOrder.DESC)
					.limit(1).offset(maxEntries.toLong() - 1)
					.first()[TraceTable.timestamp]
				
				totalDeleted += TraceTable.deleteWhere {
					(TraceTable.origin eq origin) and
							(TraceTable.namespace eq namespace) and
							(TraceTable.timestamp less cutoffTimestamp)
				}
			}
		}
		return@transaction totalDeleted
	}
	
	suspend fun trimGlobal(maxTotalEntries: Int): Int = db.transaction {
		val total = TraceTable.selectAll().count()
		if (total <= maxTotalEntries.toLong()) return@transaction 0
		deleteOldestBatch((total - maxTotalEntries).toInt())
	}
	
	suspend fun deleteOldestBatch(batchSize: Int): Int = db.transaction {
		val cutoffTimestamp = TraceTable.select(TraceTable.timestamp)
			.orderBy(TraceTable.timestamp, SortOrder.ASC)
			.limit(1).offset(batchSize.toLong() - 1)
			.firstOrNull()?.get(TraceTable.timestamp) ?: return@transaction 0
		
		TraceTable.deleteWhere { TraceTable.timestamp lessEq cutoffTimestamp }
	}
}
