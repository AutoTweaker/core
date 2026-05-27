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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object Settings : SettingService {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	
	private val cache = ConcurrentHashMap<String, SettingValue>()
	
	fun init() {
		db = H2DatabaseStore.connect("AppConfig")
		transaction(db) {
			SchemaUtils.create(ConfigTable)
		}
		cache.putAll(loadAllIntoCache())
		logger.info("Settings initialized  count={}", cache.size)
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
		upsertValue(id, value, def.description)
		cache[id] = value
		logger.debug("Setting updated by def  id={}  value={}", id, value)
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
		logger.debug("Setting updated by id  id={}  value={}", id, value)
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
		logger.debug("Setting description updated  id={}", id)
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
