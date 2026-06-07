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

package io.github.autotweaker.core.infrastructure.persistence.trace

import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant

object TraceStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	private val mutex = Mutex()
	
	fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("Traces")
		transaction(db) { SchemaUtils.create(TraceTable) }
		logger.info("TraceStore initialized  table=traces")
	}
	
	suspend fun insert(origin: String, namespace: String, content: String): Unit = mutex.withLock {
		transaction(db) {
			TraceTable.insert {
				it[TraceTable.origin] = origin
				it[TraceTable.namespace] = namespace
				it[TraceTable.timestamp] = Clock.System.now()
				it[TraceTable.content] = content
			}
		}
	}
	
	fun select(origin: String, namespace: String, timestamp: Instant): String? = transaction(db) {
		TraceTable.selectAll().where {
			(TraceTable.origin eq origin) and
					(TraceTable.namespace eq namespace) and
					(TraceTable.timestamp eq timestamp)
		}.firstOrNull()?.get(TraceTable.content)
	}
	
	fun selectOrigins(): List<String> = transaction(db) {
		TraceTable.select(TraceTable.origin).withDistinct().map { it[TraceTable.origin] }
	}
	
	fun selectNamespaces(origin: String): List<String> = transaction(db) {
		TraceTable.select(TraceTable.namespace)
			.where { TraceTable.origin eq origin }
			.withDistinct()
			.map { it[TraceTable.namespace] }
	}
	
	fun count(origin: String, namespace: String): Int = transaction(db) {
		TraceTable.selectAll()
			.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
			.count().toInt()
	}
	
	fun selectEntries(origin: String, namespace: String, range: UIntRange): List<Instant> = transaction(db) {
		val count = (range.last - range.first + 1u).toInt()
		TraceTable.select(TraceTable.timestamp)
			.where { (TraceTable.origin eq origin) and (TraceTable.namespace eq namespace) }
			.orderBy(TraceTable.timestamp)
			.limit(count).offset(range.first.toLong())
			.map { it[TraceTable.timestamp] }
	}
	
	fun delete(origin: String, namespace: String, timestamp: Instant): Unit = transaction(db) {
		TraceTable.deleteWhere {
			(TraceTable.origin eq origin) and
					(TraceTable.namespace eq namespace) and
					(TraceTable.timestamp eq timestamp)
		}
	}
}
