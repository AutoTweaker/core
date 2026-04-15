package io.github.whiteelephant.autotweaker.core.data.database

import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object Settings {
    fun init() {
        // 连接
        val dbDir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "database")
        Files.createDirectories(dbDir)
        Database.connect("jdbc:h2:${dbDir.resolve("AppConfig")};DB_CLOSE_DELAY=-1", "org.h2.Driver")

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
