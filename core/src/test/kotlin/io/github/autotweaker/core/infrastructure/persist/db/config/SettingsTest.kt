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

package io.github.autotweaker.core.infrastructure.persist.db.config

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTest {
	private val dbUrl = "jdbc:h2:mem:settings_${counter.getAndIncrement()};DB_CLOSE_DELAY=-1"
	
	private lateinit var db: Database
	private lateinit var settings: Settings
	
	companion object {
		private val counter = AtomicInteger(0)
	}
	
	@BeforeTest
	fun setUp() {
		TestServices.init()
		db = Database.connect(dbUrl, "org.h2.Driver")
		transaction(db) { SchemaUtils.create(ConfigTable) }
		val store = mockk<DatabaseStore>()
		every { store.connect(any()) } returns db
		settings = Settings(store)
	}
	
	@Test
	fun settingRoundTrip() {
		val def = fakeIntDef(7)
		
		assertEquals(7, settings.get(def))
		settings.set(def, 42)
		assertEquals(42, settings.get(def))
		assertEquals(1, nonNullColumnCount(idOf(def)))
		assertEquals(42, storedIntValue(idOf(def)))
	}
	
	@Test
	fun rowWithoutRegisteredDefIsIgnored() {
		val def = fakeIntDef(7)
		insertRow(idOf(def), SettingValue.ValInt(999))
		
		assertEquals(7, settings.get(def))
		assertTrue(settings.getAllEntries().none { it.id == idOf(def) })
	}
	
	@Test
	fun rowWithMismatchedTypeFallsBackToDefault() {
		val (id, def) = registeredDef()
		insertRow(id, mismatchValue(def))
		
		assertEquals(
			def.default,
			settings.getAllEntries().first { it.id == id }.value
		)
	}
	
	@Test
	fun setOverwritesStaleRowOfAnotherType() {
		val (id, def) = registeredDef()
		insertRow(id, mismatchValue(def))
		
		settings.set(id, def.default)
		
		assertEquals(1, nonNullColumnCount(id))
		assertTrue(settings.getAllEntries().any { it.id == id && it.value == def.default })
	}
	
	private fun fakeIntDef(default: Int): SettingDef<SettingValue.ValInt> {
		val def = mockk<SettingDef<SettingValue.ValInt>>()
		every { def.default } returns SettingValue.ValInt(default)
		return def
	}
	
	private fun registeredDef(): Pair<String, SettingDef<*>> {
		val (id, def) = SettingRegistry.getAll().entries.firstOrNull()
			?: error("No registered SettingDef found")
		return id to def
	}
	
	private fun mismatchValue(def: SettingDef<*>): SettingValue<*> =
		if (def.default is SettingValue.ValString) SettingValue.ValInt(1)
		else SettingValue.ValString("mismatch")
	
	private fun idOf(def: SettingDef<*>) = requireNotNull(def::class.qualifiedName)
	
	private fun insertRow(id: String, value: SettingValue<*>) {
		transaction(db) {
			ConfigTable.insert {
				it[keyName] = id
				when (value) {
					is SettingValue.ValByte -> it[ConfigTable.byteValue] = value.value
					is SettingValue.ValShort -> it[ConfigTable.shortValue] = value.value
					is SettingValue.ValInt -> it[ConfigTable.intValue] = value.value
					is SettingValue.ValLong -> it[ConfigTable.longValue] = value.value
					is SettingValue.ValFloat -> it[ConfigTable.floatValue] = value.value
					is SettingValue.ValDouble -> it[ConfigTable.doubleValue] = value.value
					is SettingValue.ValBoolean -> it[ConfigTable.booleanValue] = value.value
					is SettingValue.ValChar -> it[ConfigTable.charValue] = value.value.toString()
					is SettingValue.ValString -> it[ConfigTable.stringValue] = value.value
				}
			}
		}
	}
	
	private fun nonNullColumnCount(id: String): Int = transaction(db) {
		val row = ConfigTable.selectAll().where { ConfigTable.keyName eq id }.single()
		listOf(
			row[ConfigTable.byteValue],
			row[ConfigTable.shortValue],
			row[ConfigTable.intValue],
			row[ConfigTable.longValue],
			row[ConfigTable.floatValue],
			row[ConfigTable.doubleValue],
			row[ConfigTable.booleanValue],
			row[ConfigTable.charValue],
			row[ConfigTable.stringValue],
		).count { it != null }
	}
	
	private fun storedIntValue(id: String): Int? = transaction(db) {
		ConfigTable.selectAll().where { ConfigTable.keyName eq id }.single()[ConfigTable.intValue]
	}
}
