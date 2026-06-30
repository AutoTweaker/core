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

package io.github.autotweaker.core.infrastructure.persist.json.store

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.core.infrastructure.persist.db.transaction
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object JsonStoreImpl : Loggable, Traceable {
	private val cache = ConcurrentHashMap<KClass<*>, JsonStore>()
	private val json = Json { ignoreUnknownKeys = true }
	private lateinit var db: Database
	
	suspend fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("AppConfig")
		db.transaction { SchemaUtils.create(JsonStoreTable) }
		log.info("Initialized json store  table=json_store")
	}
	
	fun namespace(kClass: KClass<*>) =
		cache.computeIfAbsent(kClass) { JsonEntry(it) }
	
	private class JsonEntry(kClass: KClass<*>) : JsonStore, Traceable {
		val namespace: String = kClass.java.name
		override fun get(): JsonElement? =
			transaction(db) {
				JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq namespace }.singleOrNull()?.let { row ->
					trace.catching { json.parseToJsonElement(row[JsonStoreTable.content]) }
						.onFailure {
							log.warn(
								"Failed JSON parsing  namespace={}  reason={}",
								namespace,
								it.message
							)
						}
						.getOrNull()
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
