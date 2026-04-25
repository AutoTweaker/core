package io.github.autotweaker.core.data.settings

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object CoreConfigRegistry {
	private val logger = LoggerFactory.getLogger(CoreConfigRegistry::class.java)
	private val _items = mutableSetOf<SettingItem>()
	
	init {
		loadDefaultConfig()
	}
	
	private fun loadDefaultConfig() {
		try {
			val items = runBlocking { SerializeConfig.fetchDefaultConfig() }
			items.forEach { register(it) }
			logger.info("Loaded ${items.size} config items from remote")
		} catch (e: Exception) {
			logger.warn("Failed to load default config, use local config only", e)
		}
	}
	
	private fun register(item: SettingItem) {
		_items.add(item)
	}
	
	fun getItem(key: String): SettingItem? = _items.find { it.key.value == key }
	fun getAllItems(): Collection<SettingItem> = _items
}
