package io.github.autotweaker.core.data.json

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

object JsonStore {
	private val logger = LoggerFactory.getLogger(JsonStore::class.java)
	private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
	private var initialized = false
	
	@Synchronized
	fun init() {
		if (initialized) return
		H2DatabaseStore().connect("AppConfig")
		transaction { SchemaUtils.create(JsonStoreTable) }
		initialized = true
		logger.info("JsonStore initialized (table: json_store)")
	}
	
	private fun ensureInit() {
		if (!initialized) init()
	}
	
	fun namespace(name: String): JsonEntry {
		ensureInit()
		return JsonEntry(name)
	}
	
	class JsonEntry(private val namespace: String) {
		fun get(): JsonElement? {
			return transaction {
				JsonStoreTable.selectAll()
					.where { JsonStoreTable.namespace eq namespace }
					.singleOrNull()
					?.let { row ->
						try {
							json.parseToJsonElement(row[JsonStoreTable.content])
						} catch (e: Exception) {
							logger.warn("Failed to parse JSON for '$namespace': ${e.message}")
							null
						}
					}
			}
		}
		
		fun set(value: JsonElement) {
			val content = json.encodeToString(JsonElement.serializer(), value)
			transaction {
				JsonStoreTable.upsert {
					it[JsonStoreTable.namespace] = namespace
					it[JsonStoreTable.content] = content
				}
			}
		}
	}
}
