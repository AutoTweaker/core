package io.github.whiteelephant.autotweaker.core.data.database.settings

import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.ResultRow

fun ConfigTable.fillColumn(it: UpdateBuilder<*>, value: Any) {
    it[valString] = null
    it[valLong] = null
    it[valDouble] = null

    when (value) {
        is String -> it[valString] = value
        is Int -> it[valLong] = value.toLong()
        is Long -> it[valLong] = value
        is Boolean -> it[valLong] = if (value) 1L else 0L
        is Double -> it[valDouble] = value
        is Float -> it[valDouble] = value.toDouble()
        else -> throw IllegalArgumentException("Unsupported persistence type: ${value::class}")
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> ConfigTable.getValueFromRow(row: ResultRow, type: kotlin.reflect.KClass<T>): T? {
    return when (type) {
        String::class -> row[valString] as T?
        Int::class -> row[valLong]?.toInt() as T?
        Long::class -> row[valLong] as T?
        Boolean::class -> (row[valLong] == 1L) as T?
        Double::class -> row[valDouble] as T?
        Float::class -> row[valDouble]?.toFloat() as T?
        else -> null
    }
}
