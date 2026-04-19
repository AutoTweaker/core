package io.github.autotweaker.core.data.settings

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

private val json = Json { ignoreUnknownKeys = true }

fun ConfigTable.fillColumn(it: UpdateBuilder<*>, value: SettingItem.Value) {
	it[valJson] = json.encodeToString(SettingItem.Value.serializer(), value)
}

fun ConfigTable.getValueFromRow(row: ResultRow): SettingItem.Value? {
	val jsonStr = row[valJson]
	return try {
		json.decodeFromString(SettingItem.Value.serializer(), jsonStr)
	} catch (_: Exception) {
		null
	}
}
