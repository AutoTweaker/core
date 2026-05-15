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

import io.github.autotweaker.api.types.settings.SettingItem
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object Settings {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var db: Database
	
	@Volatile
	private var cache: List<SettingItem>? = null
	
	fun init() {
		db = H2DatabaseStore.connect("AppConfig")
		
		val rowCount = transaction(db) {
			SchemaUtils.create(ConfigTable)
			ConfigTable.selectAll().count()
		}
		
		if (rowCount == 0L) {
			val items = runBlocking { SerializeConfig.fetchDefaultConfig() }
			ConfigRegistry.init(items)
			transaction(db) { syncDb(items) }
			cache = items
			logger.info("Settings initialized from remote  count={}", items.size)
		} else {
			cache = loadAll()
			ConfigRegistry.init(cache!!)
			logger.info("Settings initialized from local  count={}", cache!!.size)
			
			launchBackgroundConfigUpdate()
		}
	}
	
	private fun launchBackgroundConfigUpdate() {
		val localDb = db
		Thread({
			runBlocking {
				runCatching { SerializeConfig.fetchDefaultConfig() }.onSuccess { remoteItems ->
					transaction(localDb) { syncDb(remoteItems) }
					ConfigRegistry.init(remoteItems)
					cache = loadAll()
					logger.info("Background config update completed  count={}", remoteItems.size)
				}.onFailure { e ->
					logger.warn("Background config update failed  reason={}", e.message)
				}
			}
		}, "config-updater").apply { isDaemon = true }.start()
	}
	
	private fun syncDb(items: List<SettingItem>) {
		val registeredKeys = items.map { it.key.value }.toSet()
		
		items.forEach { item ->
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
	
	fun get(): List<SettingItem> = cache ?: throw IllegalStateException("Settings not initialized")
	
	fun set(item: SettingItem) {
		cache ?: throw IllegalStateException("Settings not initialized")
		
		val registered = ConfigRegistry.getItem(item.key.value)
			?: throw IllegalArgumentException("Writing to unregistered key: ${item.key.value}")
		
		if (item.value::class != registered.value::class) {
			throw IllegalArgumentException(
				"Type mismatch for key '${item.key.value}': expected ${registered.value::class.simpleName}, got ${item.value::class.simpleName}"
			)
		}
		
		transaction(db) {
			ConfigTable.upsert {
				it[keyName] = item.key.value
				it[description] = item.description
				fillColumn(it, item.value)
			}
		}
		
		cache = cache!!.filterNot { it.key.value == item.key.value } + item
		logger.debug("Setting updated  key={}  value={}", item.key.value, item.value)
	}
	
	private fun loadAll(): List<SettingItem> {
		return transaction(db) {
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
