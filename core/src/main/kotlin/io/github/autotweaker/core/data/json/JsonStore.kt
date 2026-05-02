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

package io.github.autotweaker.core.data.json

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

object JsonStore {
	private val logger = LoggerFactory.getLogger(JsonStore::class.java)
	private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
	private var initialized = false
	
	@Synchronized
	fun init() {
		if (initialized) return
		H2DatabaseStore().connect("AppConfig")
		transaction { SchemaUtils.create(JsonStoreTable) }
		initialized = true
		logger.info("JsonStore initialized (table: json_store)")
	}
	
	private fun ensureInit() {
		if (!initialized) init()
	}
	
	fun namespace(name: String): JsonEntry {
		ensureInit()
		return JsonEntry(name)
	}
	
	class JsonEntry(private val namespace: String) {
		fun get(): JsonElement? {
			return transaction {
				JsonStoreTable.selectAll()
					.where { JsonStoreTable.namespace eq namespace }
					.singleOrNull()
					?.let { row ->
						try {
							json.parseToJsonElement(row[JsonStoreTable.content])
						} catch (e: Exception) {
							logger.warn("Failed to parse JSON for '$namespace': ${e.message}")
							null
						}
					}
			}
		}
		
		fun set(value: JsonElement) {
			val content = json.encodeToString(JsonElement.serializer(), value)
			transaction {
				JsonStoreTable.upsert {
					it[JsonStoreTable.namespace] = namespace
					it[JsonStoreTable.content] = content
				}
			}
		}
	}
}
