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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepSeekDataClassCoverageTest {
    
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    
    @Test
    fun `PromptTokensDetails instantiation and getter`() {
        val details = DeepSeekUsage.PromptTokensDetails(cachedTokens = 42)
        assertEquals(42, details.cachedTokens)
    }
    
    @Test
    fun `CompletionTokensDetails default instantiation`() {
        val details = DeepSeekUsage.CompletionTokensDetails()
        assertEquals(null, details.reasoningTokens)
    }
    
    @Test
    fun `Usage with promptTokensDetails`() {
        val usage = DeepSeekUsage(
            completionTokens = 10, promptTokens = 20, totalTokens = 30,
            promptTokensDetails = DeepSeekUsage.PromptTokensDetails(cachedTokens = 5)
        )
        assertEquals(5, usage.promptTokensDetails?.cachedTokens)
    }
    
    @Test
    fun `deserialize Usage with completionTokensDetails`() {
        val jsonStr = """{
            "completion_tokens":10,"prompt_tokens":20,"total_tokens":30,
            "completion_tokens_details":{"reasoning_tokens":15}
        }"""
        val usage = json.decodeFromString<DeepSeekUsage>(jsonStr)
        assertEquals(15, usage.completionTokensDetails?.reasoningTokens)
    }
    
    @Test
    fun `deserialize Usage with promptTokensDetails`() {
        val jsonStr = """{
            "completion_tokens":10,"prompt_tokens":20,"total_tokens":30,
            "prompt_tokens_details":{"cached_tokens":5}
        }"""
        val usage = json.decodeFromString<DeepSeekUsage>(jsonStr)
        assertEquals(5, usage.promptTokensDetails?.cachedTokens)
    }
    
    @Test
    fun `deserialize StreamChunk Choice to cover getIndex`() {
        val chunk = json.decodeFromString<DeepSeekStreamChunk>(
            """{
            "id":"c1","created":1715678901,"model":"m",
            "choices":[{"index":5,"delta":{"content":"test"},"finish_reason":"stop"}]
        }"""
        )
        assertEquals(5, chunk.choices[0].index)
        assertEquals("test", chunk.choices[0].delta.content)
        assertEquals(DeepSeekFinishReason.STOP, chunk.choices[0].finishReason)
    }
    
    @Test
    fun `deserialize ToolCall with null fields`() {
        val chunk = json.decodeFromString<DeepSeekStreamChunk>(
            """{
            "id":"c1","created":1715678901,"model":"m",
            "choices":[{"index":0,"delta":{"tool_calls":[{"index":0}]},"finish_reason":null}]
        }"""
        )
        val toolCall = chunk.choices[0].delta.toolCalls!![0]
        assertEquals(0, toolCall.index)
        assertEquals(null, toolCall.id)
        assertEquals(null, toolCall.function)
    }
    
    @Test
    fun `deserialize ToolCall Function with both fields`() {
        val chunk = json.decodeFromString<DeepSeekStreamChunk>(
            """{
            "id":"c1","created":1715678901,"model":"m",
            "choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"t1","function":{"name":"read","arguments":"{}"}}]},"finish_reason":"tool_calls"}]
        }"""
        )
        val func = chunk.choices[0].delta.toolCalls!![0].function!!
        assertEquals("read", func.name)
        assertEquals("{}", func.arguments)
    }
    
    @Test
    fun `deserialize Response with message fields`() {
        val response = json.decodeFromString<DeepSeekResponse>(
            """{
            "id":"r1","created":1715678901,"model":"m",
            "choices":[{"index":0,"message":{"role":"assistant","content":"ok","reasoning_content":"think"},"finish_reason":"stop"}],
            "usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}
        }"""
        )
        assertEquals("r1", response.id)
        assertEquals("ok", response.choices[0].message.content)
        assertEquals("think", response.choices[0].message.reasoningContent)
    }
    
    @Test
    fun `deserialize Request`() {
        val req = json.decodeFromString<DeepSeekRequest>(
            """{
            "model":"m","messages":[{"role":"user","content":"hi"}],
            "stream":true,"stream_options":{"include_usage":true}
        }"""
        )
        assertEquals("m", req.model)
        assertEquals(true, req.streamOptions?.includeUsage)
    }
    
    @Test
    fun `deserialize DeepSeekMessage UserMessage`() {
        val msg = json.decodeFromString<DeepSeekMessage>("""{"role":"user","content":"hello"}""")
        assertEquals("hello", (msg as DeepSeekMessage.UserMessage).content)
    }
    
    @Test
    fun `deserialize DeepSeekMessage SystemMessage with name`() {
        val msg = json.decodeFromString<DeepSeekMessage>("""{"role":"system","content":"sys","name":"n1"}""")
        assertEquals("sys", (msg as DeepSeekMessage.SystemMessage).content)
        assertEquals("n1", msg.name)
    }
    
    @Test
    fun `deserialize DeepSeekMessage ToolMessage`() {
        val msg = json.decodeFromString<DeepSeekMessage>("""{"role":"tool","content":"result","tool_call_id":"c1"}""")
        assertEquals("result", (msg as DeepSeekMessage.ToolMessage).content)
        assertEquals("c1", msg.toolCallId)
    }
    
    @Test
    fun `serialize and deserialize ToolCall Function directly`() {
        val func = DeepSeekStreamChunk.Choice.ToolCall.Function("read", "{}")
        val jsonStr = json.encodeToString(DeepSeekStreamChunk.Choice.ToolCall.Function.serializer(), func)
        val restored = json.decodeFromString(DeepSeekStreamChunk.Choice.ToolCall.Function.serializer(), jsonStr)
        assertEquals("read", restored.name)
        assertEquals("{}", restored.arguments)
    }
    
    @Test
    fun `deserialize ToolCall Function with null arguments`() {
        val func =
            json.decodeFromString(DeepSeekStreamChunk.Choice.ToolCall.Function.serializer(), """{"name":"read"}""")
        assertEquals("read", func.name)
        assertEquals(null, func.arguments)
    }
    
    @Test
    fun `deserialize ToolCall Function with both null`() {
        val func = json.decodeFromString(DeepSeekStreamChunk.Choice.ToolCall.Function.serializer(), """{}""")
        assertEquals(null, func.name)
        assertEquals(null, func.arguments)
    }
    
    @Test
    fun `deserialize ToolCall Function with explicit null`() {
        val func = json.decodeFromString(
            DeepSeekStreamChunk.Choice.ToolCall.Function.serializer(),
            """{"name":"read","arguments":null}"""
        )
        assertEquals("read", func.name)
        assertEquals(null, func.arguments)
    }
    
    @Test
    fun `deserialize StreamChunk Choice directly`() {
        val choice = json.decodeFromString(
            DeepSeekStreamChunk.Choice.serializer(), """{
            "index":3,"delta":{"content":"x"},"finish_reason":"stop"
        }"""
        )
        assertEquals(3, choice.index)
        assertEquals("x", choice.delta.content)
    }
    
    @Test
    fun `deserialize StreamChunk Choice without finishReason`() {
        val choice = json.decodeFromString(
            DeepSeekStreamChunk.Choice.serializer(), """{
            "index":0,"delta":{}
        }"""
        )
        assertEquals(0, choice.index)
        assertEquals(null, choice.finishReason)
    }
    
    @Test
    fun `serialize and deserialize PromptTokensDetails`() {
        val details = DeepSeekUsage.PromptTokensDetails(cachedTokens = 10)
        val jsonStr = json.encodeToString(DeepSeekUsage.PromptTokensDetails.serializer(), details)
        val restored = json.decodeFromString(DeepSeekUsage.PromptTokensDetails.serializer(), jsonStr)
        assertEquals(10, restored.cachedTokens)
    }
    
    @Test
    fun `deserialize PromptTokensDetails with null cachedTokens`() {
        val details = json.decodeFromString(DeepSeekUsage.PromptTokensDetails.serializer(), """{}""")
        assertEquals(null, details.cachedTokens)
    }
    
    @Test
    fun `access ToolChoice Serializer descriptor`() {
        val desc = ToolChoice.Serializer.descriptor
        assertEquals("ToolChoice", desc.serialName)
    }
}
