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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConfigSchemaTest {
	
	@Test
	fun `SettingItem Value ValByte`() {
		val v = SettingValue.ValByte(42.toByte())
		assertEquals(42.toByte(), v.value)
	}
	
	@Test
	fun `SettingItem Value ValShort`() {
		val v = SettingValue.ValShort(100.toShort())
		assertEquals(100.toShort(), v.value)
	}
	
	@Test
	fun `SettingItem Value ValInt`() {
		val v = SettingValue.ValInt(999)
		assertEquals(999, v.value)
	}
	
	@Test
	fun `SettingItem Value ValLong`() {
		val v = SettingValue.ValLong(123456789L)
		assertEquals(123456789L, v.value)
	}
	
	@Test
	fun `SettingItem Value ValFloat`() {
		val v = SettingValue.ValFloat(3.14f)
		assertEquals(3.14f, v.value)
	}
	
	@Test
	fun `SettingItem Value ValDouble`() {
		val v = SettingValue.ValDouble(2.718)
		assertEquals(2.718, v.value)
	}
	
	@Test
	fun `SettingItem Value ValBoolean`() {
		val v = SettingValue.ValBoolean(true)
		assertEquals(true, v.value)
	}
	
	@Test
	fun `SettingItem Value ValChar`() {
		val v = SettingValue.ValChar('x')
		assertEquals('x', v.value)
	}
	
	@Test
	fun `SettingItem Value ValString`() {
		val v = SettingValue.ValString("hello")
		assertEquals("hello", v.value)
	}
	
	@Test
	fun `find returns value for existing key`() {
		val item = SettingItem(
			key = SettingKey("core.test"),
			value = SettingValue.ValString("found"),
			description = "desc"
		)
		val items = listOf(item)
		assertEquals("found", items.find<String>("core.test"))
	}
	
	@Test
	fun `find throws for missing key`() {
		val items = listOf<SettingItem>()
		assertFailsWith<IllegalArgumentException> { items.find<String>("missing") }
	}
	
	@Test
	fun `find throws for type mismatch`() {
		val item = SettingItem(
			key = SettingKey("core.num"),
			value = SettingValue.ValInt(42),
			description = "desc"
		)
		val items = listOf(item)
		assertFailsWith<IllegalArgumentException> { items.find<String>("core.num") }
	}
	
	@Test
	fun `find correct type conversion`() {
		val item = SettingItem(
			key = SettingKey("core.flag"),
			value = SettingValue.ValBoolean(false),
			description = "desc"
		)
		val items = listOf(item)
		assertEquals(false, items.find<Boolean>("core.flag"))
	}
	
	@Test
	fun `SettingItem create with all fields`() {
		val item = SettingItem(
			key = SettingKey("core.test"),
			value = SettingValue.ValInt(10),
			description = "test desc"
		)
		assertEquals("core.test", item.key.value)
		assertIs<SettingValue.ValInt>(item.value)
		assertEquals("test desc", item.description)
	}
}
