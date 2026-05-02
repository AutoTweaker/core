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

package io.github.autotweaker.core.llm.provider.mimo

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MiMoMessageSerializerTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @Test
    fun `deserialize SystemMessage by role`() {
        val jsonStr = """{"role":"system","content":[{"type":"text","text":"hello"}]}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.SystemMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("hello", text)
    }
    
    @Test
    fun `deserialize DeveloperMessage by role`() {
        val jsonStr = """{"role":"developer","content":[{"type":"text","text":"hello"}]}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.DeveloperMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("hello", text)
    }
    
    @Test
    fun `deserialize UserMessage by default`() {
        val jsonStr = """{"role":"user","content":[{"type":"text","text":"hi"}]}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.UserMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("hi", text)
    }
    
    @Test
    fun `deserialize AssistantMessage by role`() {
        val jsonStr =
            """{"role":"assistant","content":[{"type":"text","text":"reply"}],"reasoning_content":"thinking"}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.AssistantMessage>(msg)
        val text = (msg.content!![0] as MiMoMessage.Content.TextPart).text
        assertEquals("reply", text)
        assertEquals("thinking", msg.reasoningContent)
    }
    
    @Test
    fun `deserialize AssistantMessage with tool calls`() {
        val jsonStr = """{
            "role":"assistant",
            "content":[{"type":"text","text":"calling"}],
            "tool_calls":[{"id":"t1","function":{"name":"read","arguments":"{}"}}]
        }"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.AssistantMessage>(msg)
        assertEquals(1, msg.toolCalls?.size)
        assertEquals("t1", msg.toolCalls!![0].id)
        assertEquals("read", msg.toolCalls[0].function.name)
    }
    
    @Test
    fun `deserialize ToolMessage by tool_call_id presence`() {
        val jsonStr = """{"role":"tool","content":[{"type":"text","text":"result"}],"tool_call_id":"call-001"}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.ToolMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("result", text)
        assertEquals("call-001", msg.toolCallId)
    }
    
    @Test
    fun `deserialize unknown role defaults to UserMessage`() {
        val jsonStr = """{"role":"unknown","content":[{"type":"text","text":"test"}]}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.UserMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("test", text)
    }
    
    @Test
    fun `deserialize AssistantMessage with null content`() {
        val jsonStr = """{"role":"assistant","content":null}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.AssistantMessage>(msg)
        assertNull(msg.content)
    }
    
    @Test
    fun `deserialize when role key is missing defaults to UserMessage`() {
        val jsonStr = """{"content":[{"type":"text","text":"hi"}]}"""
        val msg = json.decodeFromString(MiMoMessage.serializer(), jsonStr)
        assertIs<MiMoMessage.UserMessage>(msg)
        val text = (msg.content[0] as MiMoMessage.Content.TextPart).text
        assertEquals("hi", text)
    }
}
