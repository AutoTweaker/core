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
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceTest {
	
	private val json = Json { prettyPrint = false }
	
	// region construction
	
	@Test
	fun `construct with all fields`() {
		val price = Price(
			amount = BigDecimal("0.001"),
			currency = Currency.getInstance("USD"),
			unit = 1000
		)
		assertEquals(BigDecimal("0.001"), price.amount)
		assertEquals("USD", price.currency.currencyCode)
		assertEquals(1000, price.unit)
	}
	
	@Test
	fun `construct with zero amount`() {
		val price = Price(BigDecimal.ZERO, Currency.getInstance("CNY"), 1)
		assertEquals(BigDecimal.ZERO, price.amount)
	}
	
	@Test
	fun `construct with negative unit`() {
		val price = Price(BigDecimal("1.5"), Currency.getInstance("EUR"), -1)
		assertEquals(-1, price.unit)
	}
	
	// endregion
	
	// region equals
	
	@Test
	fun `equal prices are equal`() {
		val p1 = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1000)
		val p2 = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1000)
		assertEquals(p1, p2)
		assertEquals(p1.hashCode(), p2.hashCode())
	}
	
	// endregion
	
	// region serialization roundtrip
	
	@Test
	fun `serialize to JSON produces expected fields`() {
		val price = Price(BigDecimal("0.001"), Currency.getInstance("USD"), 1000)
		val serialized = json.encodeToString(Price.serializer(), price)
		assertEquals("{\"amount\":\"0.001\",\"currency\":\"USD\",\"unit\":1000}", serialized)
	}
	
	@Test
	fun `deserialize from JSON`() {
		val jsonStr = "{\"amount\":\"0.005\",\"currency\":\"CNY\",\"unit\":1000}"
		val price = json.decodeFromString(Price.serializer(), jsonStr)
		assertEquals(BigDecimal("0.005"), price.amount)
		assertEquals("CNY", price.currency.currencyCode)
		assertEquals(1000, price.unit)
	}
	
	@Test
	fun `serialize deserialize roundtrip`() {
		val original = Price(BigDecimal("3.14"), Currency.getInstance("JPY"), 1)
		val serialized = json.encodeToString(Price.serializer(), original)
		val deserialized = json.decodeFromString(Price.serializer(), serialized)
		assertEquals(original, deserialized)
	}
	
	// endregion
}
