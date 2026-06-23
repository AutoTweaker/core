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

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.Url.Companion.toUrl
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region construction & validation
	
	@Test
	fun `construct with valid HTTPS URL`() {
		val url = "https://example.com".toUrl()
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `construct with valid HTTP URL`() {
		val url = "http://example.com".toUrl()
		assertEquals("http://example.com", url.value)
	}
	
	@Test
	fun `construct with URL containing path`() {
		val url = "https://api.example.com/v1/chat".toUrl()
		assertEquals("https://api.example.com/v1/chat", url.value)
	}
	
	@Test
	fun `construct with URL containing query string`() {
		val url = "https://example.com/search?q=test".toUrl()
		assertEquals("https://example.com/search?q=test", url.value)
	}
	
	@Test
	fun `trailing slash is trimmed`() {
		val url = "https://example.com/".toUrl()
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `multiple trailing slashes are trimmed`() {
		val url = "https://example.com///".toUrl()
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `whitespace is trimmed`() {
		val url = "  https://example.com  ".toUrl()
		assertEquals("https://example.com", url.value)
	}
	
	@Test
	fun `whitespace and trailing slash both trimmed`() {
		val url = "  https://example.com/  ".toUrl()
		assertEquals("https://example.com", url.value)
	}
	
	// endregion
	
	// region invalid URLs
	
	@Test
	fun `blank URL throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			"".toUrl()
		}
		assertTrue(ex.message!!.contains("blank"))
	}
	
	@Test
	fun `whitespace-only URL throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			"   ".toUrl()
		}
		assertTrue(ex.message!!.contains("blank"))
	}
	
	@Test
	fun `invalid URL format throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			"not-a-url".toUrl()
		}
		assertTrue(ex.message!!.contains("Invalid URL"))
	}
	
	@Test
	fun `non-HTTP scheme throws`() {
		assertFailsWith<IllegalArgumentException> {
			"ftp://example.com".toUrl()
		}
		assertFailsWith<IllegalArgumentException> {
			"file:///etc/passwd".toUrl()
		}
	}
	
	@Test
	fun `relative URL throws`() {
		assertFailsWith<IllegalArgumentException> {
			"/relative/path".toUrl()
		}
	}
	
	// endregion
	
	// region serialization
	
	@Test
	fun `serialize and deserialize roundtrip`() {
		val json = Json { prettyPrint = false }
		val original = "https://api.test.com".toUrl()
		val serialized = json.encodeToString(Url.serializer(), original)
		val deserialized = json.decodeFromString(Url.serializer(), serialized)
		assertEquals(original.value, deserialized.value)
	}
	
	// endregion
	
	// region equality
	
	@Test
	fun `same URLs are equal`() {
		val a = "https://example.com".toUrl()
		val b = "https://example.com".toUrl()
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
	}
	
	@Test
	fun `different URLs are not equal`() {
		val a = "https://example.com".toUrl()
		val b = "https://other.com".toUrl()
		assertTrue(a != b)
	}
	
	// endregion
}
