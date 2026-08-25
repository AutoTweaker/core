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

package io.github.autotweaker.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnicodeTest {
	
	// region Char.toUnicodeEscape
	
	@Test
	fun `toUnicodeEscape uppercase letter`() {
		assertEquals("\\u0041", 'A'.toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape lowercase letter`() {
		assertEquals("\\u007A", 'z'.toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape null character`() {
		assertEquals("\\u0000", '\u0000'.toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape max BMP character`() {
		assertEquals("\\uFFFF", '\uFFFF'.toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape newline`() {
		assertEquals("\\u000A", '\n'.toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape space`() {
		assertEquals("\\u0020", ' '.toUnicodeEscape())
	}
	
	// endregion
	
	// region String.toUnicodeEscape
	
	@Test
	fun `toUnicodeEscape single char string`() {
		assertEquals("\\u0041", "A".toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape multiple chars`() {
		assertEquals("\\u0041\\u0042\\u0043", "ABC".toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape empty string`() {
		assertEquals("", "".toUnicodeEscape())
	}
	
	@Test
	fun `toUnicodeEscape mixed chars`() {
		assertEquals("\\u0048\\u0065\\u006C\\u006C\\u006F", "Hello".toUnicodeEscape())
	}
	
	// endregion
	
	// region roundtrip
	
	@Test
	fun `toUnicodeEscape then unescapeUnicode roundtrip`() {
		val original = "ABC"
		val escaped = original.toUnicodeEscape()
		assertEquals("\\u0041\\u0042\\u0043", escaped)
		assertEquals(original, escaped.unescapeUnicode())
	}
	
	@Test
	fun `toUnicodeEscape then unescapeUnicode roundtrip various chars`() {
		val chars = listOf('A', 'z', '0', ' ', '\n', '\uFFFF', '\u0000')
		for (c in chars) {
			val escaped = c.toUnicodeEscape()
			assertEquals(c.toString(), escaped.unescapeUnicode(), "roundtrip failed for char: $c")
		}
	}
	
	@Test
	fun `unescapeUnicode then toUnicodeEscape roundtrip`() {
		val escaped = "\\u0041\\u0042\\u0043"
		val original = escaped.unescapeUnicode()
		assertEquals("ABC", original)
		assertEquals(escaped, original.toUnicodeEscape())
	}
	
	// endregion
	
	// region unescapeUnicode
	
	@Test
	fun `unescapeUnicode passes plain string through`() {
		val input = "hello world"
		assertEquals("hello world", input.unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode with empty string`() {
		assertEquals("", "".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode escaped backslash`() {
		assertEquals("\\", "\\\\".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode escaped backslash before uXXXX preserves literal uXXXX`() {
		assertEquals("\\u0000", "\\\\u0000".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode single unicode escape`() {
		assertEquals("A", "\\u0041".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode lowercase unicode escape`() {
		assertEquals("A", "\\u0041".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode multiple unicode escapes`() {
		assertEquals("ABC", "\\u0041\\u0042\\u0043".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode mixed escapes`() {
		assertEquals("\\A", "\\\\\\u0041".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode null character`() {
		assertEquals("\u0000", "\\u0000".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode max BMP character`() {
		assertEquals("\uFFFF", "\\uFFFF".unescapeUnicode())
	}
	
	@Test
	fun `unescapeUnicode unicode escape in middle of string`() {
		assertEquals("hello   world", "hello \\u0020 world".unescapeUnicode())
	}
	
	@Test
	fun `trailing backslash throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"abc\\".unescapeUnicode(strict = true)
		}
		assertEquals("Trailing backslash at position 3", ex.message)
	}
	
	@Test
	fun `incomplete unicode escape throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"abc\\u12".unescapeUnicode(strict = true)
		}
		assertEquals("Incomplete Unicode escape sequence at position 3: expected 4 hex digits after \\u", ex.message)
	}
	
	@Test
	fun `incomplete unicode escape at end throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"\\u1".unescapeUnicode(strict = true)
		}
		assertEquals("Incomplete Unicode escape sequence at position 0: expected 4 hex digits after \\u", ex.message)
	}
	
	@Test
	fun `unknown escape sequence throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"abc\\n".unescapeUnicode(strict = true)
		}
		assertEquals("Unknown escape sequence \\n at position 3", ex.message)
	}
	
	@Test
	fun `unknown escape sequence at position 0 throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"\\t".unescapeUnicode(strict = true)
		}
		assertEquals("Unknown escape sequence \\t at position 0", ex.message)
	}
	
	@Test
	fun `nonStrict trailing backslash skips backslash`() {
		assertEquals("abc", "abc\\".unescapeUnicode(strict = false))
	}
	
	@Test
	fun `nonStrict incomplete unicode escape preserves backslash`() {
		assertEquals("abc\\u12", "abc\\u12".unescapeUnicode(strict = false))
	}
	
	@Test
	fun `nonStrict unknown escape preserves backslash`() {
		assertEquals("abc\\n", "abc\\n".unescapeUnicode(strict = false))
	}
	
	@Test
	fun `invalid hex in unicode escape throws with clear message`() {
		val ex = assertFailsWith<IllegalStateException> {
			"abc\\u00ZZ".unescapeUnicode(strict = true)
		}
		assertEquals("Invalid Unicode escape sequence \\u00ZZ at position 3: not a valid hex number", ex.message)
	}
	
	@Test
	fun `nonStrict invalid hex in unicode escape preserves backslash`() {
		assertEquals("abc\\u00ZZ", "abc\\u00ZZ".unescapeUnicode(strict = false))
	}
	
	// endregion
}
