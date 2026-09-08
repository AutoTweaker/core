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
import io.github.autotweaker.api.log
import io.github.autotweaker.core.infrastructure.persist.db.base.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.h2.H2DatabaseStore.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object MigrationApi : Loggable {
	const val APP_CONFIG = "AppConfig"
	
	private val connections = ConcurrentHashMap<String, Database>()
	
	suspend fun <T> transaction(dbName: String, block: suspend JdbcTransaction.() -> T): T =
		withContext(Dispatchers.IO) { connect(dbName).transaction { block() } }
	
	suspend fun syncSchema(dbName: String, vararg tables: Table) = transaction(dbName) {
		SchemaUtils.addMissingColumnsStatements(*tables).forEach { exec(it) }
	}
	
	suspend fun readJson(namespace: String): JsonElement? = transaction(APP_CONFIG) {
		JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq namespace }
			.singleOrNull()?.get(JsonStoreTable.content)
	}
	
	suspend fun writeJson(namespace: String, element: JsonElement) = transaction(APP_CONFIG) {
		JsonStoreTable.upsert {
			it[JsonStoreTable.namespace] = namespace
			it[JsonStoreTable.content] = element
		}
	}
	
	suspend fun exec(dbName: String, @Language("sql") sql: String) = transaction(dbName) { exec(sql) }
	
	suspend fun closeAll() {
		connections.keys.toList().forEach { name ->
			runCatching { shutdown(name) }
				.onFailure { log.error("Failed to shutdown database  db={}", name, it) }
		}
	}
	
	suspend fun shutdown(dbName: String) {
		connections.remove(dbName)?.let {
			it.transaction {
				exec("SHUTDOWN")
			}
		}
	}
	
	private fun connect(dbName: String): Database = connections.computeIfAbsent(dbName) {
		Files.createDirectories(DB_PATH)
		Database.connect(H2DatabaseStore.url(dbName), "org.h2.Driver")
	}
}
