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

package io.github.autotweaker.core.data.store.h2

import io.github.autotweaker.core.data.store.DatabaseStore
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Files
import java.nio.file.Path

class H2DatabaseStore : DatabaseStore {
	override fun connect(dbName: String) {
		val dbDir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "database")
		Files.createDirectories(dbDir)
		Database.connect("jdbc:h2:${dbDir.resolve(dbName)};DB_CLOSE_DELAY=-1", "org.h2.Driver")
	}
}
