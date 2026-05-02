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

package io.github.autotweaker.core.llm.provider.deepseek

import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeepSeekToolChoiceSerializerTest {
    
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    @Serializable
    data class Wrapper(
        val toolChoice: ToolChoice
    )
    
    @Test
    fun `serialize Simple AUTO to string`() {
        val encoded = json.encodeToString(Wrapper(ToolChoice.AUTO))
        assertEquals("""{"toolChoice":"auto"}""", encoded)
    }
    
    @Test
    fun `serialize Simple NONE to string`() {
        val encoded = json.encodeToString(Wrapper(ToolChoice.NONE))
        assertEquals("""{"toolChoice":"none"}""", encoded)
    }
    
    @Test
    fun `serialize Simple REQUIRED to string`() {
        val encoded = json.encodeToString(Wrapper(ToolChoice.REQUIRED))
        assertEquals("""{"toolChoice":"required"}""", encoded)
    }
    
    @Test
    fun `serialize Specific to object`() {
        val specific = ToolChoice.function("myFunc")
        val encoded = json.encodeToString(Wrapper(specific))
        assertEquals(
            """{"toolChoice":{"type":"function","function":{"name":"myFunc"}}}""",
            encoded
        )
    }
    
    @Test
    fun `deserialize string to Simple`() {
        val decoded = json.decodeFromString<Wrapper>("""{"toolChoice":"auto"}""")
        assertEquals(ToolChoice.AUTO, decoded.toolChoice)
    }
    
    @Test
    fun `deserialize object to Specific`() {
        val decoded = json.decodeFromString<Wrapper>(
            """{"toolChoice":{"type":"function","function":{"name":"myFunc"}}}"""
        )
        val expected = ToolChoice.function("myFunc")
        assertEquals(expected, decoded.toolChoice)
    }
    
    @Test
    fun `function factory creates Specific with correct name`() {
        val tc = ToolChoice.function("testFn")
        assert(true)
        assertEquals("testFn", tc.function.name)
    }
    
    @Test
    fun `serialize throws when encoder is not JsonEncoder`() {
        val serializer = ToolChoice.Serializer
        val fakeEncoder = mockk<Encoder>(relaxed = true)
        assertFailsWith<SerializationException> {
            serializer.serialize(fakeEncoder, ToolChoice.AUTO)
        }
    }
    
    @Test
    fun `deserialize throws when decoder is not JsonDecoder`() {
        val serializer = ToolChoice.Serializer
        val fakeDecoder = mockk<Decoder>(relaxed = true)
        assertFailsWith<SerializationException> {
            serializer.deserialize(fakeDecoder)
        }
    }
}
