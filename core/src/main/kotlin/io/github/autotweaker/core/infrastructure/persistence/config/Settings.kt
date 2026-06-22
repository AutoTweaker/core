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

package io.github.autotweaker.core.infrastructure.persistence.config

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.ConcurrentHashMap
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace.Traceable
import io.github.autotweaker.api.trace.trace

object Settings : SettingService, Loggable, Traceable {
	private val json = Json { ignoreUnknownKeys = true }
	private lateinit var db: Database
	
	private val cache = ConcurrentHashMap<String, SettingValue>()
	
	private fun fillColumn(it: UpdateBuilder<*>, value: SettingValue) {
		it[ConfigTable.valJson] = json.encodeToString(SettingValue.serializer(), value)
	}
	
	private fun getValueFromRow(row: ResultRow): SettingValue? {
		val jsonStr = row[ConfigTable.valJson]
		return trace.catching { json.decodeFromString(SettingValue.serializer(), jsonStr) }
			.onFailure { log.warn("Failed config value deserialization  key={}", row[ConfigTable.keyName]) }
			.getOrNull()
	}
	
	fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("AppConfig")
		transaction(db) {
			SchemaUtils.create(ConfigTable)
		}
		cache.putAll(loadAllIntoCache())
		log.info("Initialized settings  count={}", cache.size)
	}
	
	private fun loadAllIntoCache(): Map<String, SettingValue> = transaction(db) {
		val map = mutableMapOf<String, SettingValue>()
		ConfigTable.selectAll().forEach { row ->
			getValueFromRow(row)?.let { map[row[ConfigTable.keyName]] = it }
		}
		map
	}
	
	override fun <V : SettingValue> get(def: SettingDef<V>): V {
		val id = def::class.qualifiedName ?: error("Anonymous SettingDef not supported: ${def::class}")
		val stored = cache[id]
		@Suppress("UNCHECKED_CAST") return if (stored != null && stored::class == def.default::class) stored as V else def.default
	}
	
	override fun <V : SettingValue> set(def: SettingDef<V>, value: V) {
		val id = def::class.qualifiedName ?: error("Anonymous SettingDef not supported: ${def::class}")
		upsertValue(id, value, def.description)
		cache[id] = value
		log.debug("Updated setting by def  id={}  value={}", id, value)
	}
	
	override fun set(id: String, value: SettingValue) {
		val def = ConfigRegistry.get(id) ?: throw IllegalArgumentException("Unknown setting: $id")
		if (value::class != def.default::class) {
			throw IllegalArgumentException(
				"Type mismatch for '$id': expected ${def.default::class.simpleName}, got ${value::class.simpleName}"
			)
		}
		upsertValue(id, value, def.description)
		cache[id] = value
		log.debug("Updated setting by id  id={}  value={}", id, value)
	}
	
	private fun upsertValue(id: String, value: SettingValue, description: String) {
		transaction(db) {
			val exists =
				cache.containsKey(id) || ConfigTable.selectAll().where { ConfigTable.keyName eq id }.empty().not()
			ConfigTable.upsert {
				it[keyName] = id
				if (!exists) {
					it[ConfigTable.description] = description
				}
				fillColumn(it, value)
			}
		}
	}
	
	override fun setDescription(id: String, description: String) {
		val def = ConfigRegistry.get(id) ?: throw IllegalArgumentException("Unknown setting: $id")
		transaction(db) {
			ConfigTable.upsert {
				it[keyName] = id
				it[ConfigTable.description] = description
				fillColumn(it, def.default)
			}
		}
		if (!cache.containsKey(id)) {
			cache[id] = def.default
		}
		log.debug("Updated setting description  id={}", id)
	}
	
	override fun getAll(): List<SettingEntry> = transaction(db) {
		val stored = ConfigTable.selectAll().associate {
			it[ConfigTable.keyName] to (getValueFromRow(it) to it[ConfigTable.description])
		}
		ConfigRegistry.getAll().map { (id, def) ->
			val (storedValue, storedDesc) = stored[id] ?: (null to null)
			SettingEntry(
				id = id,
				value = storedValue ?: def.default,
				description = storedDesc ?: def.description,
			)
		}
	}
	
	override fun getDefault(id: String): SettingDef<*>? = ConfigRegistry.get(id)
}