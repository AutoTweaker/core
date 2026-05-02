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
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.math.BigDecimal
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentChatTest {
    
    private val testUrl = Url("https://api.test.com/v1")
    private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
    private val testModelInfo = ModelInfo(
        id = "test-id",
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
    
    private fun userMsg(content: String = "hello") = AgentContext.Message.User(content, null, Clock.System.now())
    
    @After
    fun cleanup() {
        unmockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
    }
    
    @Test
    fun `emits outputting and finished for successful response with content`() = runTest {
        val assistantMsg = ChatMessage.AssistantMessage(
            content = "hello world",
            createdAt = Clock.System.now(),
            reasoningContent = null,
            toolCalls = null,
        )
        val chatResult = ChatResult(
            message = assistantMsg,
            finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
            usage = Usage(100, 50, 50),
        )
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(ResilientChatResult(chatResult, null))
        }
        
        val user = userMsg("hello")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        assertTrue(results.any { it is AgentChatStreamResult.Outputting })
        assertTrue(results.any { it is AgentChatStreamResult.Finished })
        
        val outputting = results.filterIsInstance<AgentChatStreamResult.Outputting>().first()
        assertEquals("hello world", outputting.content)
        
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        assertEquals("hello world", finished.result.context.content)
        assertNotNull(finished.result.finishReason)
    }
    
    @Test
    fun `emits reasoning when reasoning content arrives`() = runTest {
        val assistantMsg = ChatMessage.AssistantMessage(
            content = "answer",
            createdAt = Clock.System.now(),
            reasoningContent = "let me think",
            toolCalls = null,
        )
        val chatResult = ChatResult(message = assistantMsg)
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(ResilientChatResult(chatResult, null))
        }
        
        val user = userMsg("question")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        val reasoning = results.filterIsInstance<AgentChatStreamResult.Reasoning>().first()
        assertEquals("let me think", reasoning.reasoningContent)
        
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        assertEquals("let me think", finished.result.context.reasoning)
        assertEquals("answer", finished.result.context.content)
    }
    
    @Test
    fun `accumulates content across multiple chunks`() = runTest {
        val now = Clock.System.now()
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(
                ResilientChatResult(
                    ChatResult(
                        message = ChatMessage.AssistantMessage("hello ", now, null, null),
                    ),
                    null,
                )
            )
            emit(
                ResilientChatResult(
                    ChatResult(
                        message = ChatMessage.AssistantMessage("world", now, null, null),
                        finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
                    ),
                    null,
                )
            )
        }
        
        val user = userMsg("greet")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        val outputtings = results.filterIsInstance<AgentChatStreamResult.Outputting>()
        assertEquals(2, outputtings.size)
        assertEquals("hello ", outputtings[0].content)
        assertEquals("hello world", outputtings[1].content)
        
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        // Finished uses msg?.content (last message content) which overrides accumulated
        assertEquals("world", finished.result.context.content)
    }
    
    @Test
    fun `emits failing on error message`() = runTest {
        val errorMsg = ChatMessage.ErrorMessage(
            content = "service unavailable",
            createdAt = Clock.System.now(),
            statusCode = io.ktor.http.HttpStatusCode.ServiceUnavailable,
        )
        val errorChatResult = ChatResult(message = errorMsg)
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(ResilientChatResult(errorChatResult, null))
        }
        
        val user = userMsg("hello")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        val failings = results.filterIsInstance<AgentChatStreamResult.Failing>()
        assertEquals(1, failings.size)
        assertEquals("service unavailable", failings[0].errors.first().content)
    }
    
    @Test
    fun `handles all models exhausted silently`() = runTest {
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            throw IllegalStateException("All candidate models exhausted without success")
        }
        
        val user = userMsg("hello")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        // Should complete without emitting Finished
        assertTrue(results.none { it is AgentChatStreamResult.Finished })
    }
    
    @Test
    fun `emits finished with tool calls when assistant message has tool calls`() = runTest {
        val assistantToolCalls = listOf(
            ChatMessage.AssistantMessage.ToolCall("call-1", "read", """{"path":"/tmp"}""")
        )
        val assistantMsg = ChatMessage.AssistantMessage(
            content = null,
            createdAt = Clock.System.now(),
            reasoningContent = null,
            toolCalls = assistantToolCalls,
        )
        val chatResult = ChatResult(message = assistantMsg)
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(ResilientChatResult(chatResult, null))
        }
        
        val user = userMsg("read file")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        val toolCalls = assertNotNull(finished.result.toolCalls)
        assertEquals(1, toolCalls.size)
        assertEquals("call-1", toolCalls[0].callId)
        assertEquals("read", toolCalls[0].name)
    }
    
    @Test
    fun `uses retrying model as result model in Finished`() = runTest {
        val fallbackModel = Model("fallback", testProvider, testModelInfo)
        val errorMsg = ChatMessage.ErrorMessage(
            content = "error",
            createdAt = Clock.System.now(),
            statusCode = io.ktor.http.HttpStatusCode.ServiceUnavailable,
        )
        val assistantMsg = ChatMessage.AssistantMessage(
            content = "recovered",
            createdAt = Clock.System.now(),
            reasoningContent = null,
            toolCalls = null,
        )
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            emit(ResilientChatResult(ChatResult(message = errorMsg), retrying = fallbackModel))
            emit(
                ResilientChatResult(
                    ChatResult(
                        message = assistantMsg,
                        finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
                    ),
                    null,
                )
            )
        }
        
        val user = userMsg("hello")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        
        assertEquals("fallback", finished.result.context.model.name)
    }
    
    @Test
    fun `carries over tool calls between chunks`() = runTest {
        val toolCalls = listOf(
            ChatMessage.AssistantMessage.ToolCall("call-1", "read", """{"path":"/tmp"}""")
        )
        val now = Clock.System.now()
        
        mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
        every {
            resilientChat(any(), any(), any(), any())
        } returns flow {
            // first chunk: has toolCalls but no content
            emit(
                ResilientChatResult(
                    ChatResult(message = ChatMessage.AssistantMessage(null, now, null, toolCalls)),
                    null,
                )
            )
            // second chunk: has content but no toolCalls → carries over
            emit(
                ResilientChatResult(
                    ChatResult(
                        message = ChatMessage.AssistantMessage("done", now, null, null),
                        finishReason = ChatResult.FinishReason("tool", ChatResult.FinishReason.Type.TOOL),
                    ),
                    null,
                )
            )
        }
        
        val user = userMsg("read file")
        val ctx = AgentContext(null, null, null, null, AgentContext.CurrentRound(user, null, null, null))
        val request = AgentChatRequest(testModel, null, null, null, ctx)
        
        val results = agentChat(request).toList()
        val finished = results.filterIsInstance<AgentChatStreamResult.Finished>().first()
        
        val tc = assertNotNull(finished.result.toolCalls)
        assertEquals(1, tc.size)
        assertEquals("call-1", tc[0].callId)
    }
}
