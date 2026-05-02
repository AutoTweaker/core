/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
