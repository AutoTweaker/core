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

package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Base64
import io.ktor.http.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class LlmCoreTypesCoverageTest {
    
    private val now = Clock.System.now()
    
    @Test
    fun `ChatMessage SystemMessage all fields`() {
        val msg = ChatMessage.SystemMessage("hello", now)
        assertEquals("hello", msg.content)
        assertEquals(now, msg.createdAt)
    }
    
    @Test
    fun `ChatMessage UserMessage with pictures`() {
        val pic = Base64("dGVzdA==")
        val msg = ChatMessage.UserMessage("hi", now, pictures = listOf(pic))
        assertEquals("hi", msg.content)
        assertEquals(1, msg.pictures?.size)
        assertEquals("dGVzdA==", msg.pictures!![0].value)
    }
    
    @Test
    fun `ChatMessage UserMessage without pictures`() {
        val msg = ChatMessage.UserMessage("hi", now)
        assertEquals("hi", msg.content)
        assertNull(msg.pictures)
    }
    
    @Test
    fun `ChatMessage AssistantMessage all fields`() {
        val tc = ChatMessage.AssistantMessage.ToolCall("id1", "read", "{}")
        val msg = ChatMessage.AssistantMessage(
            content = "reply",
            createdAt = now,
            reasoningContent = "thinking",
            toolCalls = listOf(tc),
            model = "test-model"
        )
        assertEquals("reply", msg.content)
        assertEquals("thinking", msg.reasoningContent)
        assertEquals(1, msg.toolCalls?.size)
        assertEquals("id1", msg.toolCalls!![0].id)
        assertEquals("test-model", msg.model)
    }
    
    @Test
    fun `ChatMessage AssistantMessage minimal fields`() {
        val msg = ChatMessage.AssistantMessage(content = null, createdAt = now)
        assertNull(msg.content)
        assertNull(msg.reasoningContent)
        assertNull(msg.toolCalls)
        assertNull(msg.model)
    }
    
    @Test
    fun `ChatMessage ToolMessage all fields`() {
        val msg = ChatMessage.ToolMessage("result", now, "call-1")
        assertEquals("result", msg.content)
        assertEquals("call-1", msg.toolCallId)
    }
    
    @Test
    fun `ChatMessage ErrorMessage with status`() {
        val msg = ChatMessage.ErrorMessage("error", now, HttpStatusCode.InternalServerError)
        assertEquals("error", msg.content)
        assertEquals(HttpStatusCode.InternalServerError, msg.statusCode)
    }
    
    @Test
    fun `ChatMessage ErrorMessage without status`() {
        val msg = ChatMessage.ErrorMessage("error", now, null)
        assertNull(msg.statusCode)
    }
    
    @Test
    fun `ChatRequest all fields`() {
        val params = buildJsonObject { put("key", JsonPrimitive("value")) }
        val req = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage.UserMessage("hi", now)),
            thinking = true,
            stream = true,
            maxTokens = 500,
            tools = listOf(ChatRequest.Tool("read", "desc", params)),
            toolCallRequired = true,
            temperature = 0.5,
            topP = 0.8,
            frequencyPenalty = 0.1,
            presencePenalty = 0.2,
            responseFormat = ChatRequest.ResponseFormat(ChatRequest.ResponseFormat.Type.JSON_OBJECT)
        )
        assertEquals("test-model", req.model)
        assertEquals(true, req.thinking)
        assertEquals(true, req.stream)
        assertEquals(500, req.maxTokens)
        assertEquals(1, req.tools?.size)
        assertEquals("read", req.tools!![0].name)
        assertEquals(true, req.toolCallRequired)
        assertEquals(0.5, req.temperature)
        assertEquals(0.8, req.topP)
        assertEquals(0.1, req.frequencyPenalty)
        assertEquals(0.2, req.presencePenalty)
    }
    
    @Test
    fun `ChatRequest minimal fields`() {
        val req = ChatRequest(
            model = "m",
            messages = listOf(ChatMessage.UserMessage("hi", now))
        )
        assertEquals("m", req.model)
        assertNull(req.thinking)
        assertEquals(false, req.stream)
        assertNull(req.tools)
    }
    
    @Test
    fun `ChatResult FinishReason all types`() {
        for (type in ChatResult.FinishReason.Type.entries) {
            val reason = ChatResult.FinishReason("reason", type)
            assertEquals("reason", reason.reason)
            assertEquals(type, reason.type)
        }
    }
    
    @Test
    fun `ChatResult all fields`() {
        val result = ChatResult(
            message = ChatMessage.AssistantMessage("ok", now, model = "m"),
            finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
            usage = Usage(100, 40, 60, reasoningTokens = 10, cacheHitTokens = 5)
        )
        assertEquals("ok", result.message?.content)
        assertEquals(ChatResult.FinishReason.Type.STOP, result.finishReason?.type)
        assertEquals(100, result.usage?.totalTokens)
        assertEquals(10, result.usage?.reasoningTokens)
        assertEquals(5, result.usage?.cacheHitTokens)
    }
    
    @Test
    fun `ChatResult minimal fields`() {
        val result = ChatResult()
        assertNull(result.message)
        assertNull(result.finishReason)
        assertNull(result.usage)
    }
    
    @Test
    fun `Usage all fields`() {
        val usage = Usage(
            totalTokens = 100,
            promptTokens = 40,
            completionTokens = 60,
            reasoningTokens = 10,
            cacheHitTokens = 20,
            cacheMissTokens = 20,
            imageTokens = 5
        )
        assertEquals(100, usage.totalTokens)
        assertEquals(40, usage.promptTokens)
        assertEquals(60, usage.completionTokens)
        assertEquals(10, usage.reasoningTokens)
        assertEquals(20, usage.cacheHitTokens)
        assertEquals(20, usage.cacheMissTokens)
        assertEquals(5, usage.imageTokens)
    }
    
    @Test
    fun `Usage minimal fields`() {
        val usage = Usage(10, 5, 5)
        assertEquals(10, usage.totalTokens)
        assertNull(usage.reasoningTokens)
        assertNull(usage.cacheHitTokens)
    }
    
    @Test
    fun `LlmClientLoader load deepseek`() {
        val client = LlmClientLoader.load("deepseek")
        assertEquals("deepseek", client.providerInfo.name)
    }
    
    @Test
    fun `LlmClientLoader load mimo`() {
        val client = LlmClientLoader.load("mimo")
        assertEquals("mimo", client.providerInfo.name)
    }
    
    @Test
    fun `LlmClientLoader load invalid provider`() {
        val result = runCatching { LlmClientLoader.load("nonexistent") }
        assert(result.isFailure)
    }
}
