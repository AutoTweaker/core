package io.github.autotweaker.core.llm.provider.deepseek

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepSeekMessageSerializerTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @Test
    fun `deserialize SystemMessage by role`() {
        val jsonStr = """{"role":"system","content":"hello"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.SystemMessage>(msg)
        assertEquals("hello", msg.content)
    }
    
    @Test
    fun `deserialize UserMessage by default`() {
        val jsonStr = """{"role":"user","content":"hi"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.UserMessage>(msg)
        assertEquals("hi", msg.content)
    }
    
    @Test
    fun `deserialize AssistantMessage by role`() {
        val jsonStr = """{"role":"assistant","content":"reply","reasoning_content":"thinking"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.AssistantMessage>(msg)
        assertEquals("reply", msg.content)
        assertEquals("thinking", msg.reasoningContent)
    }
    
    @Test
    fun `deserialize AssistantMessage with tool calls`() {
        val jsonStr = """{
            "role":"assistant",
            "content":"calling tool",
            "tool_calls":[{"id":"t1","function":{"name":"read","arguments":"{}"}}]
        }"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.AssistantMessage>(msg)
        assertEquals(1, msg.toolCalls?.size)
        assertEquals("t1", msg.toolCalls!![0].id)
        assertEquals("read", msg.toolCalls[0].function.name)
    }
    
    @Test
    fun `deserialize ToolMessage by tool_call_id presence`() {
        val jsonStr = """{"role":"tool","content":"result","tool_call_id":"call-001"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.ToolMessage>(msg)
        assertEquals("result", msg.content)
        assertEquals("call-001", msg.toolCallId)
    }
    
    @Test
    fun `deserialize unknown role defaults to UserMessage`() {
        val jsonStr = """{"role":"unknown","content":"test"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.UserMessage>(msg)
        assertEquals("test", msg.content)
    }
    
    @Test
    fun `deserialize AssistantMessage with null content`() {
        val jsonStr = """{"role":"assistant","content":null}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.AssistantMessage>(msg)
        assertNull(msg.content)
    }
    
    @Test
    fun `deserialize when role key is missing defaults to UserMessage`() {
        val jsonStr = """{"content":"hi"}"""
        val msg = json.decodeFromString(DeepSeekMessage.serializer(), jsonStr)
        assertIs<DeepSeekMessage.UserMessage>(msg)
        assertEquals("hi", msg.content)
    }
}
