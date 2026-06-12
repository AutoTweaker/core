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

import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import io.github.autotweaker.core.infrastructure.persistence.trace.TraceRecorderImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

object JsonStoreImpl {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val trace = TraceRecorderImpl.recorder(this::class)
	private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
	private lateinit var db: Database
	
	fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("AppConfig")
		transaction(db) { SchemaUtils.create(JsonStoreTable) }
		logger.info("Initialized json store  table=json_store")
	}
	
	fun namespace(kClass: KClass<*>): JsonStore {
		return JsonEntry(kClass)
	}
	
	private class JsonEntry(kClass: KClass<*>) : JsonStore {
		val namespace: String = kClass.java.name
		override fun get(): JsonElement? {
			return transaction(db) {
				JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq namespace }.singleOrNull()?.let { row ->
					trace.catching { json.parseToJsonElement(row[JsonStoreTable.content]) }
						.onFailure {
							logger.warn(
								"Failed JSON parsing  namespace={}  reason={}",
								namespace,
								it.message
							)
						}
						.getOrNull()
				}
			}
		}
		
		override fun set(value: JsonElement) {
			val namespace = namespace
			val content = json.encodeToString(JsonElement.serializer(), value)
			transaction(db) {
				JsonStoreTable.upsert {
					it[JsonStoreTable.namespace] = namespace
					it[JsonStoreTable.content] = content
				}
			}
		}
	}
}
