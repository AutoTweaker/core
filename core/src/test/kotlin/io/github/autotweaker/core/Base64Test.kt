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

package io.github.autotweaker.core

import kotlin.test.*

class Base64Test {
	
	// region construction & validation
	
	@Test
	fun `construct with valid Base64`() {
		val b64 = Base64("YQ==")
		assertEquals("YQ==", b64.value)
	}
	
	@Test
	fun `construct with empty string passes validation`() {
		val b64 = Base64("")
		assertEquals("", b64.value)
	}
	
	@Test
	fun `construct with invalid length throws`() {
		assertFailsWith<IllegalArgumentException> {
			Base64("abc")
		}
	}
	
	@Test
	fun `construct with invalid chars throws`() {
		assertFailsWith<IllegalArgumentException> {
			Base64("!!!!")
		}
	}
	
	@Test
	fun `construct with invalid chars though valid length throws`() {
		assertFailsWith<IllegalArgumentException> {
			Base64("____")
		}
	}
	
	// endregion
	
	// region isValid
	
	@Test
	fun `isValid returns true for valid Base64`() {
		assertTrue(Base64.isValid("YQ=="))
		assertTrue(Base64.isValid("YWJj"))
		assertTrue(Base64.isValid("YWJjZA=="))
	}
	
	@Test
	fun `isValid returns false for wrong length`() {
		assertFalse(Base64.isValid("abc"))
		assertFalse(Base64.isValid("abcde"))
	}
	
	@Test
	fun `isValid returns false for invalid characters`() {
		assertFalse(Base64.isValid("____"))
		assertFalse(Base64.isValid("ab-c"))
	}
	
	@Test
	fun `isValid returns true for empty string`() {
		assertTrue(Base64.isValid(""))
	}
	
	// endregion
	
	// region encode
	
	@Test
	fun `encode produces valid Base64 from bytes`() {
		val encoded = Base64.encode("hello".toByteArray())
		assertTrue(Base64.isValid(encoded.value))
	}
	
	@Test
	fun `encode empty bytes produces empty string`() {
		val encoded = Base64.encode(ByteArray(0))
		assertEquals("", encoded.value)
	}
	
	// endregion
	
	// region decode
	
	@Test
	fun `decode returns original bytes`() {
		val original = "hello world".toByteArray()
		val encoded = Base64.encode(original)
		val decoded = encoded.decode()
		assertEquals(original.toList(), decoded.toList(), "decoded bytes should match original")
	}
	
	@Test
	fun `decode works with known value`() {
		val b64 = Base64("YQ==")
		val decoded = b64.decode()
		assertEquals("a", String(decoded))
	}
	
	// endregion
	
	// region roundtrip
	
	@Test
	fun `encode decode roundtrip preserves data`() {
		val data = "AutoTweaker test data".toByteArray()
		val encoded = Base64.encode(data)
		val decoded = encoded.decode()
		assertEquals(data.toList(), decoded.toList())
	}
	
	// endregion
}
