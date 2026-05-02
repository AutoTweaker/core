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

import io.github.autotweaker.core.data.store.h2.H2DatabaseStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class SettingsIntegrationTest {
    
    @BeforeTest
    fun setUp() {
        // Replace Settings.store with mock that uses in-memory H2
        val mockStore = mockk<H2DatabaseStore>(relaxed = true)
        every { mockStore.connect(any()) } answers {
            Database.connect("jdbc:h2:mem:settings_test;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        }
        injectStoreField(mockStore)
        
        mockkObject(CoreConfigRegistry)
        every { CoreConfigRegistry.getItem(any()) } returns null
        every { CoreConfigRegistry.getItem("test.key1") } returns
                SettingItem(SettingKey("test.key1"), SettingItem.Value.ValString("default1"), "desc1")
        every { CoreConfigRegistry.getItem("test.key2") } returns
                SettingItem(SettingKey("test.key2"), SettingItem.Value.ValInt(42), "desc2")
        every { CoreConfigRegistry.getAllItems() } returns listOf(
            SettingItem(SettingKey("test.key1"), SettingItem.Value.ValString("default1"), "desc1"),
            SettingItem(SettingKey("test.key2"), SettingItem.Value.ValInt(42), "desc2")
        )
    }
    
    @AfterTest
    fun tearDown() {
        unmockkObject(CoreConfigRegistry)
    }
    
    @Test
    fun `init then getAll returns defaults`() {
        Settings.init()
        val all = Settings.getAll()
        assertTrue(all.isNotEmpty())
        val item = all.find { it.key.value == "test.key1" }!!
        assertEquals("default1", (item.value as SettingItem.Value.ValString).value)
    }
    
    @Test
    fun `set updates value`() {
        Settings.init()
        Settings.set(SettingItem(SettingKey("test.key1"), SettingItem.Value.ValString("updated"), "updated desc"))
        val all = Settings.getAll()
        val item = all.find { it.key.value == "test.key1" }!!
        assertEquals("updated", (item.value as SettingItem.Value.ValString).value)
    }
    
    @Test
    fun `set unregistered key throws`() {
        Settings.init()
        assertFailsWith<IllegalArgumentException> {
            Settings.set(SettingItem(SettingKey("zz.unknown"), SettingItem.Value.ValString("x"), "desc"))
        }
    }
    
    @Test
    fun `set type mismatch throws`() {
        Settings.init()
        assertFailsWith<IllegalArgumentException> {
            Settings.set(SettingItem(SettingKey("test.key1"), SettingItem.Value.ValInt(100), "desc"))
        }
    }
    
    @Test
    fun `init detects type mismatch and updates`() {
        every { CoreConfigRegistry.getItem("test.key3") } returns
                SettingItem(SettingKey("test.key3"), SettingItem.Value.ValInt(999), "desc3")
        every { CoreConfigRegistry.getAllItems() } returns listOf(
            SettingItem(SettingKey("test.key3"), SettingItem.Value.ValInt(999), "desc3")
        )
        Settings.init()
        
        val afterFirst = Settings.getAll().find { it.key.value == "test.key3" }!!
        assertTrue(afterFirst.value is SettingItem.Value.ValInt)
        
        every { CoreConfigRegistry.getItem("test.key3") } returns
                SettingItem(SettingKey("test.key3"), SettingItem.Value.ValString("updated-type"), "desc3")
        every { CoreConfigRegistry.getAllItems() } returns listOf(
            SettingItem(SettingKey("test.key3"), SettingItem.Value.ValString("updated-type"), "desc3")
        )
        
        Settings.init()
        val afterUpdate = Settings.getAll().find { it.key.value == "test.key3" }!!
        assertTrue(afterUpdate.value is SettingItem.Value.ValString)
    }
    
    @Test
    fun `init with empty registry skips delete`() {
        every { CoreConfigRegistry.getAllItems() } returns emptyList()
        every { CoreConfigRegistry.getItem(any()) } returns null
        Settings.init()
    }
    
    @Test
    fun `getAll throws when row has corrupted JSON`() {
        Settings.init()
        transaction {
            ConfigTable.insert {
                it[ConfigTable.keyName] = "test.corrupt.json"
                it[ConfigTable.description] = "bad data"
                it[ConfigTable.valJson] = "this is not valid json"
            }
        }
        assertFailsWith<IllegalStateException> { Settings.getAll() }
    }
    
    companion object {
        private fun injectStoreField(store: H2DatabaseStore) {
            val field = Settings::class.java.getDeclaredField("store")
            field.isAccessible = true
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
                .invoke(unsafe, field) as Long
            val base =
                unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
            val putObject =
                unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
            putObject.invoke(unsafe, base, offset, store)
        }
    }
}
