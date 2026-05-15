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

import io.github.autotweaker.api.types.settings.SettingItem
import org.slf4j.LoggerFactory

object ConfigRegistry {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val _items = mutableSetOf<SettingItem>()
	
	fun init(items: Collection<SettingItem>) {
		_items.clear()
		_items.addAll(items)
		logger.info("Config registry initialized  count={}", _items.size)
	}
	
	fun getItem(key: String): SettingItem? = _items.find { it.key.value == key }
	fun getAllItems(): Collection<SettingItem> = _items
}
