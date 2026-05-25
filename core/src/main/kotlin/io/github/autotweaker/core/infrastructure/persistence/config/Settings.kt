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
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.infrastructure.persistence.store.h2.H2DatabaseStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object Settings : SettingService {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	
	@Volatile
	private var cache: Map<String, SettingValue> = emptyMap()
	
	fun init() {
		db = H2DatabaseStore.connect("AppConfig")
		transaction(db) {
			SchemaUtils.create(ConfigTable)
		}
		seedDefaults()
		cache = loadAllIntoCache()
		logger.info("Settings initialized  count={}", cache.size)
	}
	
	private fun seedDefaults() {
		val existingIds = transaction(db) {
			ConfigTable.selectAll().map { it[ConfigTable.keyName] }.toSet()
		}
		for ((id, def) in ConfigRegistry.getAll()) {
			if (id !in existingIds) {
				transaction(db) {
					ConfigTable.upsert {
						it[keyName] = id
						it[description] = def.description
						fillColumn(it, def.default)
					}
				}
				logger.debug("Setting seeded  id={}", id)
			}
		}
	}
	
	private fun loadAllIntoCache(): Map<String, SettingValue> = transaction(db) {
		val map = mutableMapOf<String, SettingValue>()
		ConfigTable.selectAll().forEach { row ->
			getValueFromRow(row)?.let { map[row[ConfigTable.keyName]] = it }
		}
		map
	}
	
	override fun <V : SettingValue> get(def: SettingDef<V>): V {
		val id = def::class.qualifiedName!!
		val stored = cache[id]
		@Suppress("UNCHECKED_CAST") return if (stored != null && stored::class == def.default::class) stored as V else def.default
	}
	
	override fun <V : SettingValue> set(def: SettingDef<V>, value: V) {
		val id = def::class.qualifiedName!!
		transaction(db) {
			ConfigTable.upsert {
				it[keyName] = id
				it[description] = def.description
				fillColumn(it, value)
			}
		}
		cache = cache + (id to value)
		logger.debug("Setting updated by def  id={}  value={}", id, value)
	}
	
	override fun set(id: String, value: SettingValue) {
		val def = ConfigRegistry.get(id) ?: throw IllegalArgumentException("Unknown setting: $id")
		if (value::class != def.default::class) {
			throw IllegalArgumentException(
				"Type mismatch for '$id': expected ${def.default::class.simpleName}, got ${value::class.simpleName}"
			)
		}
		transaction(db) {
			ConfigTable.upsert {
				it[keyName] = id
				it[description] = def.description
				fillColumn(it, value)
			}
		}
		cache = cache + (id to value)
		logger.debug("Setting updated by id  id={}  value={}", id, value)
	}
	
	override fun setDescription(id: String, description: String) {
		require(ConfigRegistry.get(id) != null) { "Unknown setting: $id" }
		transaction(db) {
			ConfigTable.update({ ConfigTable.keyName eq id }) {
				it[ConfigTable.description] = description
			}
		}
		logger.debug("Setting description updated  id={}", id)
	}
	
	override fun getAll(): List<SettingEntry> = transaction(db) {
		ConfigTable.selectAll().map { row ->
			SettingEntry(
				id = row[ConfigTable.keyName],
				value = getValueFromRow(row)
					?: throw IllegalStateException("Failed to parse value for key '${row[ConfigTable.keyName]}'"),
				description = row[ConfigTable.description]
			)
		}
	}
	
	override fun getDefault(id: String): SettingDef<*>? = ConfigRegistry.get(id)
}
