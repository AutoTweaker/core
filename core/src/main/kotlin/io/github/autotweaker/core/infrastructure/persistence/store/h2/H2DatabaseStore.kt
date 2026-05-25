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

import io.github.autotweaker.core.infrastructure.persistence.store.DatabaseStore
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object H2DatabaseStore : DatabaseStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val databases = ConcurrentHashMap<String, Database>()
	
	override fun connect(dbName: String): Database =
		databases.computeIfAbsent(dbName) { name ->
			val dbDir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "database")
			Files.createDirectories(dbDir)
			val url = "jdbc:h2:${dbDir.resolve(name)};DB_CLOSE_DELAY=-1"
			logger.debug("Database connected  db={}  url={}", name, url)
			Database.connect(url, "org.h2.Driver")
		}
	
	override fun shutdown() {
		databases.values.forEach { db ->
			try {
				transaction(db) { exec("SHUTDOWN") }
			} catch (_: Exception) {
			}
		}
		databases.clear()
	}
}