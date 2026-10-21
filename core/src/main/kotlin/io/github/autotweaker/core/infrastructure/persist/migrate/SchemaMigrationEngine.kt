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

import io.github.autotweaker.api.loadService
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*
import kotlin.time.measureTimedValue

object SchemaMigrationEngine : MigratorBase() {
	private const val DB_FILE_SUFFIX = ".mv.db"
	
	private val BACKUP_PATH: Path = DB_PATH / "backup"
	private val BACKUP_TMP_PATH: Path = DB_PATH / "backup.tmp"
	private val APP_CONFIG_FILE: Path = DB_PATH / (APP_CONFIG + DB_FILE_SUFFIX)
	
	suspend fun run() {
		if (!APP_CONFIG_FILE.exists()) {
			initSchemaVersion()
			log.info(
				"Initialized new database  db={}  schemaVersion={}",
				APP_CONFIG, CURRENT_SCHEMA_VERSION
			)
			return
		}
		
		val diskVersion = readSchemaVersion() ?: error(MISSING_VERSION_MESSAGE)
		if (diskVersion > CURRENT_SCHEMA_VERSION) {
			error(
				"Database schema version $diskVersion is newer than supported version $CURRENT_SCHEMA_VERSION. " +
						"Refusing to run an older program against newer data."
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
			"Started schema migration  from={}  to={}  databases={}",
			diskVersion, CURRENT_SCHEMA_VERSION, listDbNames()
		)
		if (BACKUP_TMP_PATH.isDirectory()) {
			log.warn("Discarded incomplete backup")
			deleteDir(BACKUP_TMP_PATH)
		}
		val staleBackups = listBackupFiles()
		if (staleBackups.isNotEmpty()) {
			log.warn("Detected backup of incomplete migration  count={}", staleBackups.size)
			restoreAll()
		}
		
		val dbNames = listDbNames()
		backupAll(dbNames)
		
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
	
	private suspend fun readSchemaVersion(): Int? = transaction(APP_CONFIG) {
		runCatching {
			SchemaMetaTable.selectAll().where { SchemaMetaTable.key eq SCHEMA_VERSION_KEY }
				.singleOrNull()?.get(SchemaMetaTable.value)
		}.getOrNull()
	}
	
	private suspend fun initSchemaVersion() = transaction(APP_CONFIG) {
		SchemaUtils.create(SchemaMetaTable)
		upsertSchemaVersion()
	}
	
	private suspend fun updateSchemaVersion() = transaction(APP_CONFIG) {
		upsertSchemaVersion()
	}
	
	private suspend fun backupAll(dbNames: List<String>) = withContext(Dispatchers.IO) {
		deleteDirIfEmpty(BACKUP_PATH)
		BACKUP_TMP_PATH.createDirectories()
		dbNames.forEach { dbName ->
			val timed = measureTimedValue {
				shutdown(dbName)
				DB_PATH.resolve(dbName + DB_FILE_SUFFIX).copyTo(
					BACKUP_TMP_PATH.resolve(dbName + DB_FILE_SUFFIX),
					overwrite = true,
				)
			}
			log.info("Completed database backup  db={}  duration={}", dbName, timed.duration)
		}
		BACKUP_TMP_PATH.moveTo(BACKUP_PATH, StandardCopyOption.ATOMIC_MOVE)
	}
	
	private suspend fun checkpoint(dbName: String) =
		transaction(dbName) {
			exec("CHECKPOINT")
		}
	
	private suspend fun restoreAll() = withContext(Dispatchers.IO) {
		listBackupFiles().forEach { backup ->
			val dbName = backup.name.removeSuffix(DB_FILE_SUFFIX)
			runCatching { restore(dbName, backup) }
				.onSuccess { backup.deleteIfExists() }
				.onFailure { log.error("Failed to restore database  db={}  backup={}", dbName, backup, it) }
		}
		deleteDirIfEmpty(BACKUP_PATH)
	}
	
	private suspend fun restore(dbName: String, backup: Path) = withContext(Dispatchers.IO) {
		val timed = measureTimedValue {
			shutdown(dbName)
			backup.copyTo(
				DB_PATH / (dbName + DB_FILE_SUFFIX),
				overwrite = true,
			)
		}
		log.info("Completed database restore  db={}  duration={}", dbName, timed.duration)
	}
	
	private suspend fun deleteAllBackups() = withContext(Dispatchers.IO) {
		deleteDir(BACKUP_PATH)
		deleteDir(BACKUP_TMP_PATH)
	}
	
	private suspend fun deleteDir(dir: Path) = withContext(Dispatchers.IO) {
		if (!dir.isDirectory()) return@withContext
		dir.listDirectoryEntries().forEach { it.deleteIfExists() }
		dir.deleteIfExists()
	}
	
	private suspend fun deleteDirIfEmpty(dir: Path) = withContext(Dispatchers.IO) {
		if (!dir.isDirectory()) return@withContext
		if (dir.listDirectoryEntries().isEmpty()) dir.deleteIfExists()
	}
	
	private suspend fun listDbNames(): List<String> = withContext(Dispatchers.IO) {
		DB_PATH.listDirectoryEntries()
			.filter { it.name.endsWith(DB_FILE_SUFFIX) }
			.map { it.name.removeSuffix(DB_FILE_SUFFIX) }
	}
	
	private suspend fun listBackupFiles(): List<Path> = withContext(Dispatchers.IO) {
		if (!BACKUP_PATH.isDirectory()) emptyList()
		else BACKUP_PATH.listDirectoryEntries().filter {
			it.name.endsWith(DB_FILE_SUFFIX)
		}
	}
	
	private fun upsertSchemaVersion() {
		SchemaMetaTable.upsert {
			it[SchemaMetaTable.key] = SCHEMA_VERSION_KEY
			it[SchemaMetaTable.value] = CURRENT_SCHEMA_VERSION
		}
	}
	
	private val MISSING_VERSION_MESSAGE =
		"Missing schema version row in database '$APP_CONFIG' " +
				"(file: $APP_CONFIG_FILE). If this database predates schema versioning, initialize it manually: " +
				"CREATE TABLE IF NOT EXISTS meta (\"key\" VARCHAR(255) PRIMARY KEY, \"value\" INT); " +
				"INSERT INTO meta (\"key\", \"value\") VALUES ('schema_version', 0);"
}
