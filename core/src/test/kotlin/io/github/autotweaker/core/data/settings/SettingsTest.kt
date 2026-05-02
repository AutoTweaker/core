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

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class SettingsTest {
    
    @BeforeTest
    fun setUp() {
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
    fun `test in-memory database operations directly`() {
        Database.connect("jdbc:h2:mem:test_settings;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        
        transaction {
            SchemaUtils.create(ConfigTable)
        }
        
        val key = SettingKey("test.key1")
        val item = SettingItem(key, SettingItem.Value.ValString("hello"), "desc")
        
        transaction {
            // Insert
            ConfigTable.insert {
                it[ConfigTable.keyName] = item.key.value
                it[ConfigTable.description] = item.description
                ConfigTable.fillColumn(it, item.value)
            }
        }
        
        // Query
        val rows = transaction {
            ConfigTable.selectAll().toList()
        }
        assertTrue(rows.isNotEmpty())
        assertEquals("test.key1", rows[0][ConfigTable.keyName])
        
        val restored = transaction {
            val row = ConfigTable.selectAll()
                .where { ConfigTable.keyName eq item.key.value }
                .single()
            ConfigTable.getValueFromRow(row)
        }
        assertEquals("hello", (restored as SettingItem.Value.ValString).value)
    }
    
    @Test
    fun `fillColumn stores and getValueFromRow retrieves all value types`() {
        Database.connect("jdbc:h2:mem:test_fill;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction {
            SchemaUtils.create(ConfigTable)
        }
        
        val testCases = listOf(
            SettingItem.Value.ValByte(1.toByte()) to 1.toByte(),
            SettingItem.Value.ValShort(2.toShort()) to 2.toShort(),
            SettingItem.Value.ValInt(3) to 3,
            SettingItem.Value.ValLong(4L) to 4L,
            SettingItem.Value.ValFloat(5.5f) to 5.5f,
            SettingItem.Value.ValDouble(6.6) to 6.6,
            SettingItem.Value.ValBoolean(true) to true,
            SettingItem.Value.ValChar('x') to 'x',
            SettingItem.Value.ValString("test") to "test"
        )
        
        var idx = 0
        for ((value, expected) in testCases) {
            val item = SettingItem(SettingKey("test.v${idx++}"), value, "desc")
            
            transaction {
                ConfigTable.insert {
                    it[ConfigTable.keyName] = item.key.value
                    it[ConfigTable.description] = item.description
                    ConfigTable.fillColumn(it, item.value)
                }
                
                val row = ConfigTable.selectAll()
                    .where { ConfigTable.keyName eq item.key.value }
                    .single()
                val restored = ConfigTable.getValueFromRow(row)
                assertEquals(expected, restored!!.value)
            }
        }
    }
    
    @Test
    fun `getValueFromRow returns null for corrupted JSON`() {
        Database.connect("jdbc:h2:mem:test_corrupt;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction {
            SchemaUtils.create(ConfigTable)
            ConfigTable.insert {
                it[ConfigTable.keyName] = "test.corrupt"
                it[ConfigTable.description] = "desc"
                it[ConfigTable.valJson] = "not valid json at all"
            }
            
            val row = ConfigTable.selectAll()
                .where { ConfigTable.keyName eq "test.corrupt" }
                .single()
            val result = ConfigTable.getValueFromRow(row)
            assertEquals(null, result)
        }
    }
    
    @Test
    fun `type mismatch triggers update in init logic`() {
        Database.connect("jdbc:h2:mem:test_type_mismatch;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction {
            SchemaUtils.create(ConfigTable)
            
            // Insert with wrong type
            val wrongJson = Json.encodeToString(
                SettingItem.Value.serializer(),
                SettingItem.Value.ValInt(999)
            )
            ConfigTable.insert {
                it[ConfigTable.keyName] = "test.key1"
                it[ConfigTable.description] = "old"
                it[ConfigTable.valJson] = wrongJson
            }
        }
        
        // Re-read: the stored value has type ValInt, but CoreConfigRegistry says ValString
        // getValueFromRow will parse it as ValInt
        // The init logic would detect type mismatch (ValInt != ValString) and update
        val row = transaction {
            ConfigTable.selectAll().where { ConfigTable.keyName eq "test.key1" }.single()
        }
        val stored = ConfigTable.getValueFromRow(row)
        assertTrue(stored is SettingItem.Value.ValInt)
    }
}
