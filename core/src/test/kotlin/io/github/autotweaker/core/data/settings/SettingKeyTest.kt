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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SettingKeyTest {
	
	@Test
	fun `valid key with two segments`() {
		val key = SettingKey("core.debug")
		assertEquals("core.debug", key.value)
	}
	
	@Test
	fun `valid key with multiple segments`() {
		val key = SettingKey("aa.bb.cc.dd99")
		assertEquals("aa.bb.cc.dd99", key.value)
	}
	
	@Test
	fun `valid key with single segment`() {
		val key = SettingKey("hello")
		assertEquals("hello", key.value)
	}
	
	@Test
	fun `valid key with numbers`() {
		val key = SettingKey("section123.sub456")
		assertEquals("section123.sub456", key.value)
	}
	
	@Test
	fun `reject blank key`() {
		assertFailsWith<IllegalArgumentException> { SettingKey("") }
		assertFailsWith<IllegalArgumentException> { SettingKey("  ") }
	}
	
	@Test
	fun `reject key starting with dot`() {
		assertFailsWith<IllegalArgumentException> { SettingKey(".invalid") }
	}
	
	@Test
	fun `reject key ending with dot`() {
		assertFailsWith<IllegalArgumentException> { SettingKey("invalid.") }
	}
	
	@Test
	fun `reject key with single char segment`() {
		assertFailsWith<IllegalArgumentException> { SettingKey("a.bc") }
	}
	
	@Test
	fun `reject key with uppercase segment`() {
		assertFailsWith<IllegalArgumentException> { SettingKey("Valid.key") }
	}
	
	@Test
	fun `reject key with special chars in segment`() {
		assertFailsWith<IllegalArgumentException> { SettingKey("ab.cd_ef") }
	}
	
	@Test
	fun `equality works by value`() {
		assertEquals(SettingKey("ab.cd"), SettingKey("ab.cd"))
		assertNotEquals(SettingKey("ab.cd"), SettingKey("ef.gh"))
	}
}
