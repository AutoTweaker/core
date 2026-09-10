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

package io.github.autotweaker.core.infrastructure.persist.migrate

import io.github.autotweaker.api.CONFIG_PATH
import io.github.autotweaker.core.infrastructure.persist.db.config.ConfigTable
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.nio.file.Files
import java.nio.file.Path

object MigrateTestEnv {
	private val databaseDir: Path = CONFIG_PATH.resolve("database")
	private val appConfigUrl: String
		get() = "jdbc:h2:$databaseDir/AppConfig;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;COMPRESS=TRUE"
	
	fun clean() {
		runBlocking {
			MigratorBridge.close("AppConfig")
			MigratorBridge.close("Sessions")
			testDb?.let { db ->
				runCatching { transaction(db) { exec("SHUTDOWN") } }
				testDb = null
			}
		}
		if (Files.isDirectory(databaseDir)) {
			Files.list(databaseDir).use { stream ->
				stream.filter { it.fileName.toString().endsWith(".mv.db") }
					.forEach(Files::deleteIfExists)
			}
			listOf("backup", "backup.tmp").forEach { name ->
				val dir = databaseDir.resolve(name)
				if (Files.isDirectory(dir)) {
					Files.list(dir).use { it.forEach(Files::deleteIfExists) }
					Files.deleteIfExists(dir)
				}
			}
		}
	}
	
	fun appConfigDbFileExists(): Boolean = Files.exists(databaseDir.resolve("AppConfig.mv.db"))
	
	fun readStoredVersion(): Int? {
		val row = transaction(connect()) {
			SchemaMetaTable.selectAll().where { SchemaMetaTable.key eq SCHEMA_VERSION_KEY }
				.singleOrNull()?.get(SchemaMetaTable.value)
		}
		return row
	}
	
	fun createLegacyAppConfig() {
		transaction(connect()) { SchemaUtils.create(ConfigTable, JsonStoreTable) }
	}
	
	fun createJsonStoreTable() {
		transaction(connect()) { SchemaUtils.create(JsonStoreTable) }
	}
	
	
	fun seedSchemaVersion(version: Int) {
		transaction(connect()) {
			SchemaUtils.create(SchemaMetaTable)
			SchemaMetaTable.upsert {
				it[SchemaMetaTable.key] = SCHEMA_VERSION_KEY
				it[SchemaMetaTable.value] = version
			}
		}
	}
	
	private var testDb: Database? = null
	
	private fun connect(): Database = testDb ?: run {
		Files.createDirectories(databaseDir)
		Database.connect(appConfigUrl, "org.h2.Driver").also { testDb = it }
	}
	
	private object MigratorBridge : MigratorBase() {
		suspend fun close(dbName: String) = shutdown(dbName)
	}
}

object MigratedColumnTable : Table("settings") {
	val migrated = integer("migrated")
}
