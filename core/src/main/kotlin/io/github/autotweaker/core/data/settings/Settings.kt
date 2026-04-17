package io.github.autotweaker.core.data.settings

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

object Settings {
    private val store = H2DatabaseStore()

    fun init() {
        store.connect("AppConfig")

        transaction {
            // 建表
            SchemaUtils.create(ConfigTable)

            // 写入默认值
            CoreConfigRegistry.getAllItems().forEach { item ->
                val exists = ConfigTable.selectAll()
                    .where { ConfigTable.keyName eq item.key.value }
                    .any()

                if (!exists) {
                    ConfigTable.insert {
                        it[keyName] = item.key.value
                        it[description] = item.description
                        fillColumn(it, item.value)
                    }
                }
            }
        }
    }

    fun get(key: SettingKey): SettingItem.Value {
        CoreConfigRegistry.getItem(key.value)
            ?: throw IllegalArgumentException("Key '${key.value}' is not registered")

        return transaction {
            val row = ConfigTable.selectAll()
                .where { ConfigTable.keyName eq key.value }
                .single()
            ConfigTable.getValueFromRow(row)
                ?: throw IllegalStateException("Failed to parse value for key '${key.value}'")
        }
    }

    fun getAll(): List<SettingItem> {
        return transaction {
            ConfigTable.selectAll().map { row ->
                val key = SettingKey(row[ConfigTable.keyName])
                val value = ConfigTable.getValueFromRow(row)
                    ?: throw IllegalStateException("Failed to parse value for key '${key.value}'")
                val description = row[ConfigTable.description]
                SettingItem(key, value, description)
            }
        }
    }

    fun set(item: SettingItem) {
        // 校验是否在注册表里且数据类型匹配
        val registered = CoreConfigRegistry.getItem(item.key.value)
            ?: throw IllegalArgumentException("Writing to unregistered key: ${item.key.value}")

        if (item.value::class != registered.value::class) {
            throw IllegalArgumentException(
                "Type mismatch for key '${item.key.value}': expected ${registered.value::class.simpleName}, got ${item.value::class.simpleName}"
            )
        }

        transaction {
            ConfigTable.upsert {
                it[keyName] = item.key.value
                it[description] = item.description
                fillColumn(it, item.value)
            }
        }
    }
}
