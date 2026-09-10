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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1.config

import io.github.autotweaker.api.json
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0SettingValue
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0SettingsTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.config.V1JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.config.V1SettingsTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

object AppConfigMigrator : MigratorBase() {
	suspend fun migrate() {
		migrateSettings()
		migrateJsonStore()
	}
	
	private suspend fun migrateSettings() {
		exec(APP_CONFIG, "ALTER TABLE settings RENAME TO settings_v0")
		val v0Settings = V0SettingsTable("settings_v0")
		var count = 0
		transaction(APP_CONFIG) {
			SchemaUtils.create(V1SettingsTable)
			v0Settings.selectAll().forEach { row ->
				val key = row[v0Settings.keyName]
				val value = json.decodeFromString(V0SettingValue.serializer(), row[v0Settings.valJson])
				val setting = if (key in INT_TO_LONG_KEYS && value is V0SettingValue.ValInt) {
					V0SettingValue.ValLong(value.value.toLong())
				} else value
				V1SettingsTable.upsert {
					it[V1SettingsTable.keyName] = key
					fillColumn(it, setting)
				}
				count++
			}
		}
		exec(APP_CONFIG, "DROP TABLE settings_v0")
		log.info("Migrated settings  count={}", count)
	}
	
	private suspend fun migrateJsonStore() {
		exec(APP_CONFIG, "ALTER TABLE json_store RENAME TO json_store_v0")
		val v0Store = V0JsonStoreTable("json_store_v0")
		var count = 0
		transaction(APP_CONFIG) {
			SchemaUtils.create(V1JsonStoreTable)
			v0Store.selectAll().forEach { row ->
				V1JsonStoreTable.upsert {
					it[V1JsonStoreTable.namespace] = row[v0Store.namespace]
					it[V1JsonStoreTable.content] = json.parseToJsonElement(row[v0Store.content])
				}
				count++
			}
		}
		exec(APP_CONFIG, "DROP TABLE json_store_v0")
		log.info("Migrated json store  count={}", count)
	}
	
	private fun fillColumn(it: UpdateBuilder<*>, value: V0SettingValue<*>) {
		when (value) {
			is V0SettingValue.ValByte -> it[V1SettingsTable.byteValue] = value.value
			is V0SettingValue.ValShort -> it[V1SettingsTable.shortValue] = value.value
			is V0SettingValue.ValInt -> it[V1SettingsTable.intValue] = value.value
			is V0SettingValue.ValLong -> it[V1SettingsTable.longValue] = value.value
			is V0SettingValue.ValFloat -> it[V1SettingsTable.floatValue] = value.value
			is V0SettingValue.ValDouble -> it[V1SettingsTable.doubleValue] = value.value
			is V0SettingValue.ValBoolean -> it[V1SettingsTable.booleanValue] = value.value
			is V0SettingValue.ValChar -> it[V1SettingsTable.charValue] = value.value.toString()
			is V0SettingValue.ValString -> it[V1SettingsTable.stringValue] = value.value
		}
	}
	
	private val INT_TO_LONG_KEYS = setOf(
		"io.github.autotweaker.core.infrastructure.persist.db.trace.TraceSettings.MaxEntriesPerNamespace",
		"io.github.autotweaker.core.infrastructure.persist.db.trace.TraceSettings.MaxTotalEntries",
		"io.github.autotweaker.core.infrastructure.persist.db.trace.TraceSettings.CleanupBatchSize",
	)
}
