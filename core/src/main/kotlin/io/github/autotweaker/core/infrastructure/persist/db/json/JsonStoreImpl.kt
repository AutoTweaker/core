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

package io.github.autotweaker.core.infrastructure.persist.db.json

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class JsonStoreImpl(store: DatabaseStore) : Loggable, Traceable, DbStore(
	store, "AppConfig", JsonStoreTable
) {
	private val cache = ConcurrentHashMap<KClass<*>, JsonStore>()
	
	fun namespace(kClass: KClass<*>) = cache.computeIfAbsent(kClass) {
		object : JsonStore {
			val javaName: String = kClass.java.name
			override fun get(): JsonElement? =
				transaction(db) {
					JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq javaName }
						.singleOrNull()?.let { row ->
							trace.catching { json.parseToJsonElement(row[JsonStoreTable.content]) }
								.onFailure { e ->
									log.error(
										"Failed JSON parsing  namespace={}",
										javaName, e
									)
								}.getOrNull()
						}
				}
			
			override fun set(value: JsonElement) {
				val content = json.encodeToString(value)
				transaction(db) {
					JsonStoreTable.upsert {
						it[JsonStoreTable.namespace] = javaName
						it[JsonStoreTable.content] = content
					}
				}
			}
		}
	}
}
