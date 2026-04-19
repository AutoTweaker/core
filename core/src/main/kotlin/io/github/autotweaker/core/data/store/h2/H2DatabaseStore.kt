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
