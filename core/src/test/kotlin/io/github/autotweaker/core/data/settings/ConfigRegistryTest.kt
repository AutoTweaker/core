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

import kotlin.test.*

class ConfigRegistryTest {
	
	private val testItem = SettingItem(
		SettingKey("test.key.abc"),
		SettingItem.Value.ValString("test value"),
		"test description"
	)
	
	@BeforeTest
	fun setUp() {
		val field = ConfigRegistry::class.java.getDeclaredField("_items")
		field.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		val items = field.get(ConfigRegistry) as MutableSet<SettingItem>
		items.clear()
		items.add(testItem)
	}
	
	@AfterTest
	fun tearDown() {
		val field = ConfigRegistry::class.java.getDeclaredField("_items")
		field.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		val items = field.get(ConfigRegistry) as MutableSet<SettingItem>
		items.clear()
	}
	
	@Test
	fun `getItem returns null for unknown key`() {
		val result = ConfigRegistry.getItem("nonexistent.zz99")
		assertNull(result)
	}
	
	@Test
	fun `getAllItems returns items loaded from cache`() {
		val all = ConfigRegistry.getAllItems()
		assertTrue(all.isNotEmpty())
		assertTrue(all.contains(testItem))
	}
	
	@Test
	fun `getItem finds item by key`() {
		val result = ConfigRegistry.getItem("test.key.abc")
		assertNotNull(result)
	}
}
