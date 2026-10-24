/*
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

package io.github.autotweaker.core.infrastructure.persist.db.base.h2

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.transaction
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.div

object H2DatabaseStore : DatabaseStore, Loggable, Traceable {
	private val databases = ConcurrentHashMap<String, Database>()
	
	override fun connect(dbName: String, pluginId: String?): Database {
		// 不为空，不包含';'，不包含'..'
		require(dbName.isNotBlank() && !dbName.contains(';') && !dbName.contains(".."))
		val name = if (pluginId == null) dbName else "plugins/$pluginId/$dbName"
		return databases.computeIfAbsent(name) {
			val url = url(name)
			log.info("Connected database  db={}  plugin={}  url={}", dbName, pluginId, url)
			Database.connect(url, "org.h2.Driver")
		}
	}
	
	fun url(name: String): String = "jdbc:h2:${DB_PATH / name};" +
			"DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;COMPRESS=TRUE"
	
	suspend fun shutdown() {
		databases.values.forEachParallel { db ->
			trace.catching { db.transaction { exec("SHUTDOWN") } }
		}
		databases.clear()
	}
}
