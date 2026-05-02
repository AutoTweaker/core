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

package io.github.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class InstantAsLongSerializerTest {
    
    private val json = Json { encodeDefaults = true }
    
    @Serializable
    data class Wrapper(
        @Serializable(with = InstantAsLongSerializer::class)
        val instant: Instant
    )
    
    @Test
    fun `serialize Instant to Unix epoch seconds`() {
        val t = Instant.fromEpochSeconds(1715678901)
        val wrapper = Wrapper(t)
        val encoded = json.encodeToString(wrapper)
        
        assertEquals("""{"instant":1715678901}""", encoded)
    }
    
    @Test
    fun `deserialize Unix epoch seconds to Instant`() {
        val jsonStr = """{"instant":1715678901}"""
        val wrapper = json.decodeFromString<Wrapper>(jsonStr)
        
        assertEquals(Instant.fromEpochSeconds(1715678901), wrapper.instant)
    }
    
    @Test
    fun `deserialize epoch zero`() {
        val jsonStr = """{"instant":0}"""
        val wrapper = json.decodeFromString<Wrapper>(jsonStr)
        
        assertEquals(Instant.fromEpochSeconds(0), wrapper.instant)
    }
}
