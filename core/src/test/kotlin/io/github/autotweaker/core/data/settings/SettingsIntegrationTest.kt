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

import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.settings.SettingItem
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.nio.file.Files
import kotlin.test.*

class SettingsIntegrationTest {
	
	private val tmpHome = Files.createTempDirectory("autotweaker_settings_test")
	private val originalHome = System.getProperty("user.home")
	
	@BeforeTest
	fun setUp() {
		System.setProperty("user.home", tmpHome.toString())
		
		mockkObject(SerializeConfig)
		coEvery { SerializeConfig.fetchDefaultConfig() } returns listOf(
			SettingItem(SettingKey("test.key1"), SettingValue.ValString("default1"), "desc1"),
			SettingItem(SettingKey("test.key2"), SettingValue.ValInt(42), "desc2")
		)
		
		mockkObject(ConfigRegistry)
		every { ConfigRegistry.getItem(any()) } returns null
		every { ConfigRegistry.getItem("test.key1") } returns
				SettingItem(SettingKey("test.key1"), SettingValue.ValString("default1"), "desc1")
		every { ConfigRegistry.getItem("test.key2") } returns
				SettingItem(SettingKey("test.key2"), SettingValue.ValInt(42), "desc2")
		every { ConfigRegistry.getAllItems() } returns listOf(
			SettingItem(SettingKey("test.key1"), SettingValue.ValString("default1"), "desc1"),
			SettingItem(SettingKey("test.key2"), SettingValue.ValInt(42), "desc2")
		)
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(ConfigRegistry)
		unmockkObject(SerializeConfig)
		H2DatabaseStore.shutdown()
		if (originalHome != null) {
			System.setProperty("user.home", originalHome)
		}
		tmpHome.toFile().deleteRecursively()
	}
	
	@Test
	fun `init then getAll returns defaults`() {
		Settings.init()
		val all = Settings.get()
		assertTrue(all.isNotEmpty())
		val item = all.find { it.key.value == "test.key1" }!!
		assertEquals("default1", (item.value as SettingValue.ValString).value)
	}
	
	@Test
	fun `set updates value`() {
		Settings.init()
		Settings.set(
			SettingItem(
				SettingKey("test.key1"),
				SettingValue.ValString("updated"),
				"updated desc"
			)
		)
		val all = Settings.get()
		val item = all.find { it.key.value == "test.key1" }!!
		assertEquals("updated", (item.value as SettingValue.ValString).value)
	}
	
	@Test
	fun `set unregistered key throws`() {
		Settings.init()
		assertFailsWith<IllegalArgumentException> {
			Settings.set(SettingItem(SettingKey("zz.unknown"), SettingValue.ValString("x"), "desc"))
		}
	}
	
	@Test
	fun `set type mismatch throws`() {
		Settings.init()
		assertFailsWith<IllegalArgumentException> {
			Settings.set(SettingItem(SettingKey("test.key1"), SettingValue.ValInt(100), "desc"))
		}
	}
	
	@Test
	fun `init detects type mismatch and updates`() {
		every { ConfigRegistry.getItem("test.key3") } returns
				SettingItem(SettingKey("test.key3"), SettingValue.ValInt(999), "desc3")
		every { ConfigRegistry.getAllItems() } returns listOf(
			SettingItem(SettingKey("test.key3"), SettingValue.ValInt(999), "desc3")
		)
		coEvery { SerializeConfig.fetchDefaultConfig() } returns listOf(
			SettingItem(SettingKey("test.key3"), SettingValue.ValInt(999), "desc3")
		)
		Settings.init()
		
		val afterFirst = Settings.get().find { it.key.value == "test.key3" }!!
		assertTrue(afterFirst.value is SettingValue.ValInt)
		
		every { ConfigRegistry.getItem("test.key3") } returns
				SettingItem(SettingKey("test.key3"), SettingValue.ValString("updated-type"), "desc3")
		every { ConfigRegistry.getAllItems() } returns listOf(
			SettingItem(SettingKey("test.key3"), SettingValue.ValString("updated-type"), "desc3")
		)
		coEvery { SerializeConfig.fetchDefaultConfig() } returns listOf(
			SettingItem(SettingKey("test.key3"), SettingValue.ValString("updated-type"), "desc3")
		)
		
		Settings.init()
		// Background config update runs asynchronously — poll until cache reflects the type change
		var afterUpdate: SettingItem? = null
		repeat(50) {
			afterUpdate = Settings.get().find { it.key.value == "test.key3" }
			if (afterUpdate?.value is SettingValue.ValString) return@repeat
			Thread.sleep(50)
		}
		assertTrue(afterUpdate?.value is SettingValue.ValString)
	}
	
	@Test
	fun `init with empty registry skips delete`() {
		every { ConfigRegistry.getAllItems() } returns emptyList()
		every { ConfigRegistry.getItem(any()) } returns null
		coEvery { SerializeConfig.fetchDefaultConfig() } returns emptyList()
		Settings.init()
	}
}
