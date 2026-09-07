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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.loadService
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.db.base.h2.H2DatabaseStore.DB_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object SchemaMigrationEngine : Loggable {
	private val api = MigrationApi
	
	private const val DB_FILE_SUFFIX = ".mv.db"
	private const val ZIP_SUFFIX = ".zip"
	
	private val BACKUP_PATH: Path = DB_PATH.resolve("backup")
	private val APP_CONFIG_FILE: Path = DB_PATH.resolve(api.APP_CONFIG + DB_FILE_SUFFIX)
	
	suspend fun run() {
		if (!Files.exists(APP_CONFIG_FILE)) {
			initSchemaVersion()
			log.info(
				"Initialized new database  db={}  schemaVersion={}",
				api.APP_CONFIG, CURRENT_SCHEMA_VERSION
			)
			return
		}
		
		val diskVersion = readSchemaVersion() ?: error(MISSING_VERSION_MESSAGE)
		if (diskVersion > CURRENT_SCHEMA_VERSION) {
			error(
				"Database schema version $diskVersion is newer than supported version $CURRENT_SCHEMA_VERSION " +
						"(file: $APP_CONFIG_FILE). Refusing to run an older program against newer data."
			)
		}
		if (diskVersion == CURRENT_SCHEMA_VERSION) {
			log.debug("Schema version is up to date  version={}", diskVersion)
			return
		}
		
		val pendingVersions = (diskVersion + 1)..CURRENT_SCHEMA_VERSION
		val migrations = loadService<DataMigration>()
		val versions = migrations.mapTo(mutableSetOf()) { it.targetVersion }
		require(versions == pendingVersions.toSet()) {
			"Schema migration chain mismatch: expected versions $pendingVersions, found ${versions.sorted()}"
		}
		
		log.info(
			"Starting schema migration  from={}  to={}  databases={}",
			diskVersion, CURRENT_SCHEMA_VERSION, listDbNames()
		)
		val staleBackups = listBackupZips()
		if (staleBackups.isNotEmpty()) {
			log.warn("Detected backup of incomplete migration  count={}", staleBackups.size)
			restoreAll()
		}
		
		val dbNames = listDbNames()
		dbNames.forEach { backup(it) }
		
		runCatching {
			for (version in pendingVersions) {
				migrations.single { it.targetVersion == version }.migrate()
				log.info("Applied schema migration  version={}", version)
			}
			updateSchemaVersion()
		}.onFailure { e ->
			log.error("Failed to apply schema migration", e)
			restoreAll()
		}.getOrThrow()
		
		deleteAllBackups()
		dbNames.forEach { checkpoint(it) }
		log.info("Completed schema migration  from={}  to={}", diskVersion, CURRENT_SCHEMA_VERSION)
	}
	
	private suspend fun readSchemaVersion(): Int? = api.transaction(api.APP_CONFIG) {
		runCatching {
			SchemaMetaTable.selectAll().where { SchemaMetaTable.key eq SCHEMA_VERSION_KEY }
				.singleOrNull()?.get(SchemaMetaTable.value)
		}.getOrNull()
	}
	
	private suspend fun initSchemaVersion() = api.transaction(api.APP_CONFIG) {
		SchemaUtils.create(SchemaMetaTable)
		upsertSchemaVersion()
	}
	
	private suspend fun updateSchemaVersion() = api.transaction(api.APP_CONFIG) {
		upsertSchemaVersion()
	}
	
	private suspend fun backup(dbName: String) = withContext(Dispatchers.IO) {
		Files.createDirectories(BACKUP_PATH)
		val zip = BACKUP_PATH.resolve(dbName + ZIP_SUFFIX)
		api.transaction(dbName) {
			exec("BACKUP TO '${zip.toString().replace("'", "''")}'")
		}
	}
	
	private suspend fun checkpoint(dbName: String) =
		api.transaction(dbName) {
			exec("CHECKPOINT")
		}
	
	private suspend fun restoreAll() = withContext(Dispatchers.IO) {
		listBackupZips().forEach { zip ->
			val dbName = zip.fileName.toString().removeSuffix(ZIP_SUFFIX)
			runCatching { restore(dbName, zip) }
				.onSuccess { Files.deleteIfExists(zip) }
				.onFailure { log.error("Failed to restore database  db={}  backup={}", dbName, zip, it) }
		}
	}
	
	private suspend fun restore(dbName: String, zip: Path) = withContext(Dispatchers.IO) {
		api.shutdown(dbName)
		Files.deleteIfExists(DB_PATH.resolve(dbName + DB_FILE_SUFFIX))
		Files.deleteIfExists(DB_PATH.resolve("$dbName.trace.db"))
		ZipInputStream(Files.newInputStream(zip)).use { zis ->
			var entry = zis.nextEntry
			while (entry != null) {
				Files.copy(
					zis, DB_PATH.resolve(Path.of(entry.name).fileName.toString()),
					StandardCopyOption.REPLACE_EXISTING
				)
				zis.closeEntry()
				entry = zis.nextEntry
			}
		}
	}
	
	private suspend fun deleteAllBackups() = withContext(Dispatchers.IO) {
		if (Files.isDirectory(BACKUP_PATH)) Files.list(BACKUP_PATH).use {
			it.forEach(Files::deleteIfExists)
		}
	}
	
	private suspend fun listDbNames(): List<String> = withContext(Dispatchers.IO) {
		Files.list(DB_PATH).use { stream ->
			stream.filter { it.fileName.toString().endsWith(DB_FILE_SUFFIX) }
				.map { it.fileName.toString().removeSuffix(DB_FILE_SUFFIX) }
				.toList()
		}
	}
	
	private suspend fun listBackupZips(): List<Path> = withContext(Dispatchers.IO) {
		if (!Files.isDirectory(BACKUP_PATH)) emptyList()
		else Files.list(BACKUP_PATH).use { stream ->
			stream.filter {
				it.fileName.toString().endsWith(ZIP_SUFFIX)
			}.toList()
		}
	}
	
	private fun upsertSchemaVersion() {
		SchemaMetaTable.upsert {
			it[SchemaMetaTable.key] = SCHEMA_VERSION_KEY
			it[SchemaMetaTable.value] = CURRENT_SCHEMA_VERSION
		}
	}
	
	private val MISSING_VERSION_MESSAGE =
		"Missing schema version row in database '${api.APP_CONFIG}' " +
				"(file: $APP_CONFIG_FILE). If this database predates schema versioning, initialize it manually: " +
				"CREATE TABLE IF NOT EXISTS meta (\"key\" VARCHAR(255) PRIMARY KEY, \"value\" INT); " +
				"INSERT INTO meta (\"key\", \"value\") VALUES ('schema_version', 0);"
}
