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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlTest {
	
	// region construction & validation
	
	@Test
	fun `construct with valid HTTPS URL`() {
		val url = Url("https://example.com")
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `construct with valid HTTP URL`() {
		val url = Url("http://example.com")
		assertEquals("http://example.com", url.value)
	}
	
	@Test
	fun `construct with URL containing path`() {
		val url = Url("https://api.example.com/v1/chat")
		assertEquals("https://api.example.com/v1/chat", url.value)
	}
	
	@Test
	fun `construct with URL containing query string`() {
		val url = Url("https://example.com/search?q=test")
		assertEquals("https://example.com/search?q=test", url.value)
	}
	
	@Test
	fun `trailing slash is trimmed`() {
		val url = Url("https://example.com/")
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `multiple trailing slashes are trimmed`() {
		val url = Url("https://example.com///")
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `whitespace is trimmed`() {
		val url = Url("  https://example.com  ")
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `whitespace and trailing slash both trimmed`() {
		val url = Url("  https://example.com/  ")
		assertEquals("https://example.com", url.value)
	}
	
	// endregion
	
	// region invalid URLs
	
	@Test
	fun `blank URL throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			Url("")
		}
		assertTrue(ex.message!!.contains("blank"))
	}
	
	@Test
	fun `whitespace-only URL throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			Url("   ")
		}
		assertTrue(ex.message!!.contains("blank"))
	}
	
	@Test
	fun `invalid URL format throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			Url("not-a-url")
		}
		assertTrue(ex.message!!.contains("Invalid URL"))
	}
	
	@Test
	fun `non-HTTP scheme throws`() {
		assertFailsWith<IllegalArgumentException> {
			Url("ftp://example.com")
		}
		assertFailsWith<IllegalArgumentException> {
			Url("file:///etc/passwd")
		}
	}
	
	@Test
	fun `relative URL throws`() {
		assertFailsWith<IllegalArgumentException> {
			Url("/relative/path")
		}
	}
	
	// endregion
	
	// region serialization
	
	@Test
	fun `serialize and deserialize roundtrip`() {
		val json = Json { prettyPrint = false }
		val original = Url("https://api.test.com")
		val serialized = json.encodeToString(Url.serializer(), original)
		val deserialized = json.decodeFromString(Url.serializer(), serialized)
		assertEquals(original.value, deserialized.value)
	}
	
	// endregion
	
	// region equality
	
	@Test
	fun `same URLs are equal`() {
		val a = Url("https://example.com")
		val b = Url("https://example.com")
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
	}
	
	@Test
	fun `different URLs are not equal`() {
		val a = Url("https://example.com")
		val b = Url("https://other.com")
		assertTrue(a != b)
	}
	
	// endregion
}
