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

package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.Price
import io.github.autotweaker.core.Provider.Model.*
import io.github.autotweaker.core.Provider.Model.TokenPrice.PriceTier
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.ktor.http.*
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class AgentChatDataTest {
    
    private val testUrl = Url("https://api.test.com/v1")
    private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
    private val testModelInfo = ModelInfo(
        id = "test-model-id",
        contextWindow = 128000,
        maxOutputTokens = 4096,
        price = TokenPrice(
            inputPrice = listOf(PriceTier(0, null, testPrice)),
            outputPrice = listOf(PriceTier(0, null, testPrice))
        ),
        supportsStreaming = true,
        supportsToolCalls = true,
        supportsReasoning = true,
        supportsImage = false,
        supportsJsonOutput = true,
    )
    private val testProvider = Provider("test-provider", testUrl, "sk-test", emptyList())
    private val testModel = Model("test-model", testProvider, testModelInfo, Config(0.7, 2048, null, null))
    
    @Test
    fun `construct request with all fields`() {
        val context = AgentContext(null, "system prompt", null, null, null)
        val tools = listOf(
            io.github.autotweaker.core.llm.ChatRequest.Tool(
                "read",
                "read file",
                kotlinx.serialization.json.Json.parseToJsonElement("{}")
            )
        )
        val fallbackModels = listOf(testModel)
        val request = AgentChatRequest(
            model = testModel,
            fallbackModels = fallbackModels,
            thinking = true,
            tools = tools,
            context = context,
        )
        assertEquals(testModel, request.model)
        assertEquals(fallbackModels, request.fallbackModels)
        assertEquals(true, request.thinking)
        assertEquals(tools, request.tools)
        assertEquals(context, request.context)
    }
    
    @Test
    fun `construct request with null optionals`() {
        val context = AgentContext(null, null, null, null, null)
        val request = AgentChatRequest(testModel, null, null, null, context)
        assertNull(request.fallbackModels)
        assertNull(request.thinking)
        assertNull(request.tools)
    }
    
    @Test
    fun `failing result contains errors`() {
        val now = Clock.System.now()
        val errors = listOf(
            AgentChatStreamResult.Failing.Error("error 1", HttpStatusCode.InternalServerError, null, now),
            AgentChatStreamResult.Failing.Error("error 2", HttpStatusCode.ServiceUnavailable, testModel, now),
        )
        val failing = AgentChatStreamResult.Failing(errors)
        assertEquals(2, failing.errors.size)
        assertEquals("error 1", failing.errors[0].content)
        assertEquals(HttpStatusCode.InternalServerError, failing.errors[0].statusCode)
        assertNull(failing.errors[0].retrying)
        assertEquals("error 2", failing.errors[1].content)
        assertEquals(testModel, failing.errors[1].retrying)
    }
    
    @Test
    fun `reasoning result contains content`() {
        val reasoning = AgentChatStreamResult.Reasoning("thinking step by step")
        assertEquals("thinking step by step", reasoning.reasoningContent)
    }
    
    @Test
    fun `outputting result with reasoning`() {
        val output = AgentChatStreamResult.Outputting(reasoningContent = "think", content = "hello")
        assertEquals("think", output.reasoningContent)
        assertEquals("hello", output.content)
    }
    
    @Test
    fun `outputting result without reasoning`() {
        val output = AgentChatStreamResult.Outputting(reasoningContent = null, content = "hello")
        assertNull(output.reasoningContent)
        assertEquals("hello", output.content)
    }
    
    @Test
    fun `finished result with tool calls and finish reason`() {
        val now = Clock.System.now()
        val assistantMsg = AgentContext.Message.Assistant(
            reasoning = "think",
            content = "done",
            model = testModel,
            timestamp = now,
            usage = Usage(100, 50, 50),
        )
        val toolCalls = listOf(
            AgentContext.CurrentRound.PendingToolCall("id1", "read", testModel, "{}", null, now)
        )
        val finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP)
        val result = AgentChatStreamResult.Finished.Result(assistantMsg, toolCalls, finishReason)
        val finished = AgentChatStreamResult.Finished(result)
        
        assertEquals(assistantMsg, finished.result.context)
        assertEquals(toolCalls, finished.result.toolCalls)
        assertEquals(finishReason, finished.result.finishReason)
        assertEquals(1, toolCalls.size)
    }
    
    @Test
    fun `finished result without tool calls`() {
        val now = Clock.System.now()
        val assistantMsg = AgentContext.Message.Assistant(null, "done", testModel, now, null)
        val result = AgentChatStreamResult.Finished.Result(assistantMsg, null, null)
        val finished = AgentChatStreamResult.Finished(result)
        
        assertNull(finished.result.toolCalls)
        assertNull(finished.result.finishReason)
        assertEquals("done", finished.result.context.content)
    }
}
