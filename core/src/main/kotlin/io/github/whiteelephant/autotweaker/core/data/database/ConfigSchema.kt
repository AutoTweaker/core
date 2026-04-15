package io.github.whiteelephant.autotweaker.core.data.database

import org.jetbrains.exposed.sql.Table

object ConfigTable : Table("core_settings") {
    val keyName = varchar("key_name", 255)

    val valString = text("val_string").nullable()
    val valLong = long("val_long").nullable()
    val valDouble = double("val_double").nullable()

    override val primaryKey = PrimaryKey(keyName)
}

data class SettingItem<T : Any>(
    val key: SettingKey,
    val value: T
)
