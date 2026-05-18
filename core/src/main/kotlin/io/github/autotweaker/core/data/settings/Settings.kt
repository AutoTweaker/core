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

package io.github.autotweaker.core.data.settings

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object Settings : SettingService {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	
	fun init() {
		db = H2DatabaseStore.connect("AppConfig")
		transaction(db) {
			SchemaUtils.create(ConfigTable)
		}
		seedDefaults()
		logger.info("Settings initialized  count={}", ConfigRegistry.getAll().size)
	}
	
	private fun seedDefaults() {
		val existingIds = transaction(db) {
			ConfigTable.selectAll().map { it[ConfigTable.keyName] }.toSet()
		}
		for ((id, def) in ConfigRegistry.getAll()) {
			if (id !in existingIds) {
				transaction(db) {
					ConfigTable.insert {
						it[keyName] = id
						it[description] = def.description
						fillColumn(it, def.default)
					}
				}
				logger.debug("Setting seeded  id={}", id)
			}
		}
	}
	
	override fun <V : SettingValue> get(def: SettingDef<V>): V {
		val id = def::class.qualifiedName!!
		val row = transaction(db) {
			ConfigTable.selectAll().where { ConfigTable.keyName eq id }.singleOrNull()
		} ?: return def.default
		
		val stored = ConfigTable.getValueFromRow(row) ?: return def.default
		@Suppress("UNCHECKED_CAST") return if (stored::class == def.default::class) stored as V else def.default
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
		logger.debug("Setting updated by id  id={}  value={}", id, value)
	}
	
	override fun getAll(): List<SettingEntry> = transaction(db) {
		ConfigTable.selectAll().map { row ->
			SettingEntry(
				id = row[ConfigTable.keyName],
				value = ConfigTable.getValueFromRow(row)
					?: throw IllegalStateException("Failed to parse value for key '${row[ConfigTable.keyName]}'"),
				description = row[ConfigTable.description]
			)
		}
	}
}
