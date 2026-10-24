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

package io.github.autotweaker.core.application.impl

import io.github.autotweaker.api.debug.DbDebugAPI
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class DbDebugAPIImpl(private val databaseStore: DatabaseStore) : DbDebugAPI {
	override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
		if (!DB_PATH.exists()) return@withContext emptyList()
		DB_PATH.listDirectoryEntries("*$DB_FILE_SUFFIX")
			.map { it.name.removeSuffix(DB_FILE_SUFFIX) }
			.sorted()
	}
	
	override suspend fun open(name: String): Database {
		val file = DB_PATH.resolve(name + DB_FILE_SUFFIX)
		require(file.parent == DB_PATH && file.exists()) { "Unknown database: $name" }
		return databaseStore.connect(name)
	}
	
	companion object {
		private const val DB_FILE_SUFFIX = ".mv.db"
	}
}
