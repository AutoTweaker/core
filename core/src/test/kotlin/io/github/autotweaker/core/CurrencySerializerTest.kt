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

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencySerializerTest {
	
	private val json = Json { prettyPrint = false }
	
	// region descriptor
	
	@Test
	fun `descriptor has correct serial name`() {
		assertEquals("Currency", CurrencySerializer.descriptor.serialName)
	}
	
	@Test
	fun `descriptor has STRING kind`() {
		assertEquals(PrimitiveKind.STRING, CurrencySerializer.descriptor.kind)
	}
	
	// endregion
	
	// region serialize
	
	@Test
	fun `serialize USD`() {
		val serialized = json.encodeToString(CurrencySerializer, Currency.getInstance("USD"))
		assertEquals("\"USD\"", serialized)
	}
	
	@Test
	fun `serialize CNY`() {
		val serialized = json.encodeToString(CurrencySerializer, Currency.getInstance("CNY"))
		assertEquals("\"CNY\"", serialized)
	}
	
	@Test
	fun `serialize JPY`() {
		val serialized = json.encodeToString(CurrencySerializer, Currency.getInstance("JPY"))
		assertEquals("\"JPY\"", serialized)
	}
	
	// endregion
	
	// region deserialize
	
	@Test
	fun `deserialize USD`() {
		val result = json.decodeFromString(CurrencySerializer, "\"USD\"")
		assertEquals("USD", result.currencyCode)
	}
	
	@Test
	fun `deserialize CNY`() {
		val result = json.decodeFromString(CurrencySerializer, "\"CNY\"")
		assertEquals("CNY", result.currencyCode)
	}
	
	// endregion
	
	// region roundtrip
	
	@Test
	fun `serialize deserialize roundtrip`() {
		val original = Currency.getInstance("EUR")
		val serialized = json.encodeToString(CurrencySerializer, original)
		val deserialized = json.decodeFromString(CurrencySerializer, serialized)
		assertEquals(original.currencyCode, deserialized.currencyCode)
	}
	
	// endregion
}
