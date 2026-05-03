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
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BigDecimalSerializerTest {
	
	private val json = Json { prettyPrint = false }
	
	// region descriptor
	
	@Test
	fun `descriptor has correct serial name`() {
		assertEquals("BigDecimal", BigDecimalSerializer.descriptor.serialName)
	}
	
	@Test
	fun `descriptor has STRING kind`() {
		assertEquals(PrimitiveKind.STRING, BigDecimalSerializer.descriptor.kind)
	}
	
	// endregion
	
	// region serialize
	
	@Test
	fun `serialize positive value`() {
		val serialized = json.encodeToString(BigDecimalSerializer, BigDecimal("123.45"))
		assertEquals("\"123.45\"", serialized)
	}
	
	@Test
	fun `serialize zero`() {
		val serialized = json.encodeToString(BigDecimalSerializer, BigDecimal.ZERO)
		assertEquals("\"0\"", serialized)
	}
	
	@Test
	fun `serialize negative value`() {
		val serialized = json.encodeToString(BigDecimalSerializer, BigDecimal("-99.99"))
		assertEquals("\"-99.99\"", serialized)
	}
	
	@Test
	fun `serialize uses toPlainString not scientific notation`() {
		val serialized = json.encodeToString(BigDecimalSerializer, BigDecimal("1E+2"))
		assertEquals("\"100\"", serialized)
	}
	
	// endregion
	
	// region deserialize
	
	@Test
	fun `deserialize returns correct value`() {
		val result = json.decodeFromString(BigDecimalSerializer, "\"123.456\"")
		assertEquals(BigDecimal("123.456"), result)
	}
	
	@Test
	fun `deserialize zero`() {
		val result = json.decodeFromString(BigDecimalSerializer, "\"0\"")
		assertEquals(BigDecimal.ZERO, result)
	}
	
	// endregion
	
	// region roundtrip
	
	@Test
	fun `serialize deserialize roundtrip`() {
		val original = BigDecimal("98765.43210")
		val serialized = json.encodeToString(BigDecimalSerializer, original)
		val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
		assertEquals(original, deserialized)
	}
	
	// endregion
}
