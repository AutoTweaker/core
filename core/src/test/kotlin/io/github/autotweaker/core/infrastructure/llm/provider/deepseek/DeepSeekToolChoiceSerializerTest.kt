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

package io.github.autotweaker.core.infrastructure.llm.provider.deepseek

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepSeekToolChoiceSerializerTest {
	
	private val json = Json {
		encodeDefaults = true
		ignoreUnknownKeys = true
	}
	
	@Serializable
	data class Wrapper(
		val toolChoice: String? = null
	)
	
	@Test
	fun `serialize toolChoice to string`() {
		val encoded = json.encodeToString(Wrapper("auto"))
		assertEquals("""{"toolChoice":"auto"}""", encoded)
	}
	
	@Test
	fun `serialize null toolChoice`() {
		val encoded = json.encodeToString(Wrapper(null))
		assertEquals("""{"toolChoice":null}""", encoded)
	}
	
	@Test
	fun `deserialize string to toolChoice`() {
		val decoded = json.decodeFromString<Wrapper>("""{"toolChoice":"auto"}""")
		assertEquals("auto", decoded.toolChoice)
	}
	
	@Test
	fun `deserialize null toolChoice`() {
		val decoded = json.decodeFromString<Wrapper>("""{"toolChoice":null}""")
		assertNull(decoded.toolChoice)
	}
	
	@Test
	fun `deserialize missing toolChoice defaults to null`() {
		val decoded = json.decodeFromString<Wrapper>("""{}""")
		assertNull(decoded.toolChoice)
	}
}
