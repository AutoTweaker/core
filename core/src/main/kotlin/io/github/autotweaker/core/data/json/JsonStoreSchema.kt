package io.github.autotweaker.core.data.json

import org.jetbrains.exposed.v1.core.Table

object JsonStoreTable : Table("json_store") {
	val namespace = varchar("namespace", 255)
	val content = text("content")
	
	override val primaryKey = PrimaryKey(namespace)
}
