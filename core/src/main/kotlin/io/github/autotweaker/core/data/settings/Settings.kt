package io.github.autotweaker.core.data.settings

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Suppress("unused")
object Settings {
	private val store = H2DatabaseStore()
	
	fun init() {
		store.connect("AppConfig")
		
		transaction {
			// 建表
			SchemaUtils.create(ConfigTable)
			
			// 写入默认值，类型不匹配时强制重置
			val registeredKeys = CoreConfigRegistry.getAllItems().map { it.key.value }.toSet()
			CoreConfigRegistry.getAllItems().forEach { item ->
				val row = ConfigTable.selectAll()
					.where { ConfigTable.keyName eq item.key.value }
					.singleOrNull()
				
				if (row == null) {
					ConfigTable.insert {
						it[keyName] = item.key.value
						it[description] = item.description
						fillColumn(it, item.value)
					}
				} else {
					val existingValue = ConfigTable.getValueFromRow(row)
					if (existingValue == null || existingValue::class != item.value::class) {
						ConfigTable.update({ ConfigTable.keyName eq item.key.value }) {
							it[description] = item.description
							fillColumn(it, item.value)
						}
					}
				}
			}
			
			// 删除注册表中不存在的多余行（注册表为空时跳过，防止远程配置拉取失败时清空本地数据）
			if (registeredKeys.isNotEmpty()) {
				ConfigTable.deleteWhere { ConfigTable.keyName notInList registeredKeys }
			}
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
