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

import io.github.autotweaker.api.types.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * "abc" 的标准 SHA-256 测试向量。
 */
private const val ABC_HEX = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

@Serializable
private data class Sha256Holder(val hash: Sha256)

class Sha256Test {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region serialization
	
	@Test
	fun `serialize direct to hex string`() {
		val sha = Sha256.hash("abc".toByteArray())
		val json = Json.encodeToString(Sha256.serializer(), sha)
		assertEquals("\"$ABC_HEX\"", json)
	}
	
	@Test
	fun `deserialize direct from hex string`() {
		val decoded = Json.decodeFromString(Sha256.serializer(), "\"$ABC_HEX\"")
		assertEquals(ABC_HEX, decoded.toString())
	}
	
	@Test
	fun `round trip direct`() {
		val sha = Sha256.hash("hello".toByteArray())
		val json = Json.encodeToString(Sha256.serializer(), sha)
		val decoded = Json.decodeFromString(Sha256.serializer(), json)
		assertEquals(sha, decoded)
	}
	
	@Test
	fun `round trip as data class field`() {
		val holder = Sha256Holder(Sha256.hash("hello".toByteArray()))
		val json = Json.encodeToString(holder)
		assertEquals("""{"hash":"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"}""", json)
		val decoded = Json.decodeFromString<Sha256Holder>(json)
		assertEquals(holder, decoded)
	}
	
	@Test
	fun `decode holder from known hex`() {
		val decoded = Json.decodeFromString<Sha256Holder>("""{"hash":"$ABC_HEX"}""")
		assertEquals(Sha256(ABC_HEX), decoded.hash)
	}
	
	@Test
	fun `reject invalid hex`() {
		assertFailsWith<IllegalArgumentException> { Json.decodeFromString<Sha256Holder>("""{"hash":"zz7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"}""") }
	}
	
	// endregion
	
	// region construction & hashing
	
	@Test
	fun `compute abc vector`() {
		assertEquals(ABC_HEX, Sha256.hash("abc".toByteArray()).toString())
	}
	
	@Test
	fun `construct from bytes round trip`() {
		val bytes = (0 until 32).map { it.toByte() }.toByteArray()
		val sha = Sha256(bytes)
		assertEquals(bytes.toList(), sha.bytes.toList())
	}
	
	@Test
	fun `reject wrong byte count`() {
		assertFailsWith<IllegalArgumentException> { Sha256(ByteArray(16)) }
	}
	
	// endregion
}
