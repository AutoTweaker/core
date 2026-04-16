package io.github.whiteelephant.autotweaker.core.data.database.settings

import io.github.whiteelephant.autotweaker.core.data.database.store.h2.H2DatabaseStore
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
                        fillColumn(it, item.value)
                    }
                }
            }
        }
    }

    inline fun <reified T : Any> get(key: String): T {
        val item = CoreConfigRegistry.getItem(key)
            ?: throw IllegalArgumentException("Key '$key' is not registered")

        return transaction {
            val row = ConfigTable.selectAll()
                .where { ConfigTable.keyName eq key }
                .singleOrNull()

            // 库里面有就解析，没就返回默认值
            if (row != null) {
                ConfigTable.getValueFromRow(row, T::class) ?: (item.value as T)
            } else {
                item.value as T
            }
        }
    }

    fun set(key: String, value: Any) {
        // 校验是否在注册表里
        CoreConfigRegistry.getItem(key) ?: throw IllegalArgumentException("Writing to unregistered key: $key")

        transaction {
            ConfigTable.upsert {
                it[keyName] = key
                fillColumn(it, value)
            }
        }
    }
}
