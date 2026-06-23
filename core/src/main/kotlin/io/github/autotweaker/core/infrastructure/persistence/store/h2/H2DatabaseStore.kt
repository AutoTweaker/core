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

package io.github.autotweaker.core.infrastructure.persistence.store.h2

import io.github.autotweaker.api.*
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object H2DatabaseStore : DatabaseStore, Loggable, Traceable {
	private val databases = ConcurrentHashMap<String, Database>()
	
	override fun connect(dbName: String): Database = databases.computeIfAbsent(dbName) { name ->
		val dbDir = CONFIG_PATH.resolve("database")
		Files.createDirectories(dbDir)
		val url = "jdbc:h2:${dbDir.resolve(name)};DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0"
		log.info("Connected database  db={}  url={}", name, url)
		Database.connect(url, "org.h2.Driver")
	}
	
	override fun shutdown() {
		databases.values.forEach { db ->
			trace.catching { transaction(db) { exec("SHUTDOWN") } }
		}
		databases.clear()
	}
}
