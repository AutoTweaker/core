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

package io.github.autotweaker.core.infrastructure.persist.settings

import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConfigSchemaTest {
	
	@Test
	fun `SettingValue ValByte`() {
		val v = SettingValue.ValByte(42.toByte())
		assertEquals(42.toByte(), v.value)
	}
	
	@Test
	fun `SettingValue ValShort`() {
		val v = SettingValue.ValShort(100.toShort())
		assertEquals(100.toShort(), v.value)
	}
	
	@Test
	fun `SettingValue ValInt`() {
		val v = SettingValue.ValInt(999)
		assertEquals(999, v.value)
	}
	
	@Test
	fun `SettingValue ValLong`() {
		val v = SettingValue.ValLong(123456789L)
		assertEquals(123456789L, v.value)
	}
	
	@Test
	fun `SettingValue ValFloat`() {
		val v = SettingValue.ValFloat(3.14f)
		assertEquals(3.14f, v.value)
	}
	
	@Test
	fun `SettingValue ValDouble`() {
		val v = SettingValue.ValDouble(2.718)
		assertEquals(2.718, v.value)
	}
	
	@Test
	fun `SettingValue ValBoolean`() {
		val v = SettingValue.ValBoolean(true)
		assertEquals(true, v.value)
	}
	
	@Test
	fun `SettingValue ValChar`() {
		val v = SettingValue.ValChar('x')
		assertEquals('x', v.value)
	}
	
	@Test
	fun `SettingValue ValString`() {
		val v = SettingValue.ValString("hello")
		assertEquals("hello", v.value)
	}
	
	@Test
	fun `SettingEntry create with all fields`() {
		val item = SettingEntry(
			id = "core.test",
			value = SettingValue.ValInt(10),
			description = "test desc"
		)
		assertEquals("core.test", item.id)
		assertIs<SettingValue.ValInt>(item.value)
		assertEquals("test desc", item.description)
	}
	
	@Test
	fun `SettingEntry find by id`() {
		val item = SettingEntry(
			id = "core.flag",
			value = SettingValue.ValBoolean(false),
			description = "desc"
		)
		val items = listOf(item)
		val found = items.find { it.id == "core.flag" }
		assertEquals(false, (found!!.value as SettingValue.ValBoolean).value)
	}
	
	@Test
	fun `SettingEntry find returns null for missing id`() {
		val items = listOf<SettingEntry>()
		val found = items.find { it.id == "missing" }
		assertEquals(null, found)
	}
}
