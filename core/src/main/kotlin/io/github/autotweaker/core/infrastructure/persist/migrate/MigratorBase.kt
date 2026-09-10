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
import io.github.autotweaker.api.json
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.db.base.h2.H2DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.transaction
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.nio.file.Files
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

abstract class MigratorBase : Loggable {
	protected suspend fun <T> transaction(dbName: String, block: suspend JdbcTransaction.() -> T): T =
		withContext(Dispatchers.IO) { connect(dbName).transaction { block() } }
	
	protected suspend fun syncSchema(dbName: String, vararg tables: Table) = transaction(dbName) {
		SchemaUtils.addMissingColumnsStatements(*tables).forEach { exec(it) }
	}
	
	protected suspend fun exec(dbName: String, @Language("sql") sql: String) =
		transaction(dbName) { exec(sql) }
	
	protected suspend inline fun <reified Old : Any, reified New : Any> migrateJson(
		namespace: String,
		noinline transform: (Old) -> New,
	): Boolean = migrateJson(namespace, serializer<Old>(), serializer<New>(), transform)
	
	protected suspend fun <Old : Any, New : Any> migrateJson(
		namespace: String,
		oldSerializer: KSerializer<Old>,
		newSerializer: KSerializer<New>,
		transform: (Old) -> New,
	): Boolean {
		val element = readJson(namespace) ?: return false
		val old = json.decodeFromJsonElement(oldSerializer, element)
		writeJson(namespace, json.encodeToJsonElement(newSerializer, transform(old)))
		return true
	}
	
	protected suspend fun readJson(namespace: String): JsonElement? = transaction(APP_CONFIG) {
		JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq namespace }
			.singleOrNull()?.get(JsonStoreTable.content)
	}
	
	protected suspend fun writeJson(namespace: String, element: JsonElement) = transaction(APP_CONFIG) {
		JsonStoreTable.upsert {
			it[JsonStoreTable.namespace] = namespace
			it[JsonStoreTable.content] = element
		}
	}
	
	protected suspend fun shutdown(dbName: String) {
		connections.remove(dbName)?.let { TransactionManager.closeAndUnregister(it) }
		if (!Files.exists(DB_PATH.resolve("$dbName.mv.db"))) return
		withContext(Dispatchers.IO) {
			DriverManager.getConnection(H2DatabaseStore.url(dbName)).use { connection ->
				connection.createStatement().use { it.execute("SHUTDOWN") }
			}
		}
	}
	
	private fun connect(dbName: String): Database = connections.computeIfAbsent(dbName) {
		Files.createDirectories(DB_PATH)
		Database.connect(H2DatabaseStore.url(dbName), "org.h2.Driver")
	}
	
	companion object {
		const val APP_CONFIG = "AppConfig"
		
		private val connections = ConcurrentHashMap<String, Database>()
	}
}
