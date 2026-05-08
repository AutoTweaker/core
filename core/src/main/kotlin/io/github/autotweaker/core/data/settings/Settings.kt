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

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


object Settings {
	private val store = H2DatabaseStore()
	
	@Volatile
	private var cache: List<SettingItem>? = null
	
	fun init() {
		store.connect("AppConfig")
		
		transaction {
			SchemaUtils.create(ConfigTable)
			
			val registeredKeys = CoreConfigRegistry.getAllItems().map { it.key.value }.toSet()
			CoreConfigRegistry.getAllItems().forEach { item ->
				val row = ConfigTable.selectAll().where { ConfigTable.keyName eq item.key.value }.singleOrNull()
				
				if (row == null) {
					ConfigTable.insert {
						it[keyName] = item.key.value
						it[description] = item.description
						fillColumn(it, item.value)
					}
				} else {
					val existingValue = ConfigTable.getValueFromRow(row)
					if (existingValue == null || existingValue::class != item.value::class) {
						ConfigTable.update({ ConfigTable.keyName eq item.key.value }) {
							it[description] = item.description
							fillColumn(it, item.value)
						}
					}
				}
			}
			
			if (registeredKeys.isNotEmpty()) {
				ConfigTable.deleteWhere { ConfigTable.keyName notInList registeredKeys }
			}
		}
		
		cache = loadAll()
	}
	
	fun get(): List<SettingItem> = cache ?: throw IllegalStateException("Settings not initialized")
	
	fun set(item: SettingItem) {
		cache ?: throw IllegalStateException("Settings not initialized")
		
		val registered = CoreConfigRegistry.getItem(item.key.value)
			?: throw IllegalArgumentException("Writing to unregistered key: ${item.key.value}")
		
		if (item.value::class != registered.value::class) {
			throw IllegalArgumentException(
				"Type mismatch for key '${item.key.value}': expected ${registered.value::class.simpleName}, got ${item.value::class.simpleName}"
			)
		}
		
		transaction {
			ConfigTable.upsert {
				it[keyName] = item.key.value
				it[description] = item.description
				fillColumn(it, item.value)
			}
		}
		
		cache = cache!!.filterNot { it.key.value == item.key.value } + item
	}
	
	private fun loadAll(): List<SettingItem> {
		return transaction {
			ConfigTable.selectAll().map { row ->
				val key = SettingKey(row[ConfigTable.keyName])
				val value = ConfigTable.getValueFromRow(row)
					?: throw IllegalStateException("Failed to parse value for key '${key.value}'")
				val description = row[ConfigTable.description]
				SettingItem(key, value, description)
			}
		}
	}
}