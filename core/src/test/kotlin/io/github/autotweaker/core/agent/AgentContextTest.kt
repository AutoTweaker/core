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

package io.github.autotweaker.core.agent

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.llm.Usage
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentContextTest {
    
    private val mockModel: Model = mockk(relaxed = true)
    private val now: kotlin.time.Instant = Clock.System.now()
    
    // region Message.User
    
    @Test
    fun `User message with content`() {
        val msg = AgentContext.Message.User("hello", null, now)
        assertEquals("hello", msg.content)
        assertNull(msg.images)
        assertEquals(now, msg.timestamp)
    }
    
    @Test
    fun `User message with images`() {
        val img = Base64("aaaa")
        val msg = AgentContext.Message.User("hello", listOf(img), now)
        assertEquals(listOf(img), msg.images)
    }
    
    @Test
    fun `User message with null content`() {
        val msg = AgentContext.Message.User(null, null, now)
        assertNull(msg.content)
    }
    
    // endregion
    
    // region Message.Assistant
    
    @Test
    fun `Assistant message with content`() {
        val msg = AgentContext.Message.Assistant(null, "answer", mockModel, now, null)
        assertEquals("answer", msg.content)
        assertNull(msg.reasoning)
        assertEquals(mockModel, msg.model)
        assertNull(msg.usage)
    }
    
    @Test
    fun `Assistant message with reasoning`() {
        val msg = AgentContext.Message.Assistant("thinking", "answer", mockModel, now, Usage(10, 5, 5))
        assertEquals("thinking", msg.reasoning)
        assertEquals("answer", msg.content)
        assertEquals(Usage(10, 5, 5), msg.usage)
    }
    
    @Test
    fun `Assistant message with null content and reasoning`() {
        val msg = AgentContext.Message.Assistant(null, null, mockModel, now, null)
        assertNull(msg.content)
        assertNull(msg.reasoning)
    }
    
    // endregion
    
    // region Message.Tool
    
    @Test
    fun `Tool message with success result`() {
        val call = AgentContext.Message.Tool.Call("""{"cmd":"ls"}""", "test", now, mockModel)
        val result = AgentContext.Message.Tool.Result(
            "output", now, AgentContext.Message.Tool.Result.Status.SUCCESS
        )
        val msg = AgentContext.Message.Tool("bash", call, "call-1", result)
        
        assertEquals("bash", msg.name)
        assertEquals("call-1", msg.callId)
        assertEquals("""{"cmd":"ls"}""", msg.call.arguments)
        assertEquals("test", msg.call.reason)
        assertEquals("output", msg.result.content)
        assertEquals(AgentContext.Message.Tool.Result.Status.SUCCESS, msg.result.status)
    }
    
    @Test
    fun `Tool message with failure status`() {
        val call = AgentContext.Message.Tool.Call("{}", null, now, mockModel)
        val result = AgentContext.Message.Tool.Result(
            "error", now, AgentContext.Message.Tool.Result.Status.FAILURE
        )
        val msg = AgentContext.Message.Tool("read", call, "call-2", result)
        
        assertEquals("read", msg.name)
        assertEquals(AgentContext.Message.Tool.Result.Status.FAILURE, msg.result.status)
    }
    
    @Test
    fun `Tool message with timeout status`() {
        val result = AgentContext.Message.Tool.Result(
            "timeout", now, AgentContext.Message.Tool.Result.Status.TIMEOUT
        )
        assertEquals(AgentContext.Message.Tool.Result.Status.TIMEOUT, result.status)
    }
    
    @Test
    fun `Tool message with cancelled status`() {
        val result = AgentContext.Message.Tool.Result(
            "cancelled", now, AgentContext.Message.Tool.Result.Status.CANCELLED
        )
        assertEquals(AgentContext.Message.Tool.Result.Status.CANCELLED, result.status)
    }
    
    @Test
    fun `Tool call with null reason`() {
        val call = AgentContext.Message.Tool.Call("{}", null, now, mockModel)
        assertNull(call.reason)
    }
    
    // endregion
    
    // region CompletedRound
    
    @Test
    fun `CompletedRound with turns and final message`() {
        val userMsg = AgentContext.Message.User("hello", null, now)
        val assistantMsg = AgentContext.Message.Assistant(null, "hi", mockModel, now, null)
        val turn = AgentContext.Turn(assistantMsg, emptyList())
        val round = AgentContext.CompletedRound(userMsg, listOf(turn), assistantMsg)
        
        assertEquals(userMsg, round.userMessage)
        assertEquals(1, round.turns!!.size)
        assertEquals(assistantMsg, round.finalAssistantMessage)
    }
    
    @Test
    fun `CompletedRound with null turns and final message`() {
        val userMsg = AgentContext.Message.User("hello", null, now)
        val round = AgentContext.CompletedRound(userMsg, null, null)
        
        assertNull(round.turns)
        assertNull(round.finalAssistantMessage)
    }
    
    // endregion
    
    // region CurrentRound
    
    @Test
    fun `CurrentRound with user message only`() {
        val userMsg = AgentContext.Message.User("hello", null, now)
        val round = AgentContext.CurrentRound(userMsg, null)
        
        assertEquals(userMsg, round.userMessage)
        assertNull(round.turns)
        assertNull(round.assistantMessage)
        assertNull(round.pendingToolCalls)
    }
    
    @Test
    fun `CurrentRound with pending tool calls`() {
        val userMsg = AgentContext.Message.User("read file", null, now)
        val pending = listOf(
            AgentContext.CurrentRound.PendingToolCall(
                "c1", "read_file", mockModel, """{"path":"/tmp"}""", "need to read", now
            )
        )
        val round = AgentContext.CurrentRound(userMsg, null, null, pending)
        
        val calls = round.pendingToolCalls!!
        assertEquals(1, calls.size)
        assertEquals("c1", calls[0].callId)
        assertEquals("read_file", calls[0].name)
        assertEquals(mockModel, calls[0].model)
        assertEquals("""{"path":"/tmp"}""", calls[0].arguments)
        assertEquals("need to read", calls[0].reason)
    }
    
    @Test
    fun `PendingToolCall with null reason`() {
        val pending = AgentContext.CurrentRound.PendingToolCall(
            "c1", "bash_run", mockModel, "{}", null, now
        )
        assertNull(pending.reason)
    }
    
    // endregion
    
    // region Turn
    
    @Test
    fun `Turn holds assistant message and tools`() {
        val assistantMsg = AgentContext.Message.Assistant(null, "done", mockModel, now, null)
        val call = AgentContext.Message.Tool.Call("{}", null, now, mockModel)
        val result = AgentContext.Message.Tool.Result("ok", now, AgentContext.Message.Tool.Result.Status.SUCCESS)
        val toolMsg = AgentContext.Message.Tool("bash", call, "c1", result)
        val turn = AgentContext.Turn(assistantMsg, listOf(toolMsg))
        
        assertEquals(assistantMsg, turn.assistantMessage)
        assertEquals(1, turn.tools.size)
        assertEquals("bash", turn.tools[0].name)
    }
    
    @Test
    fun `Turn with empty tools`() {
        val assistantMsg = AgentContext.Message.Assistant(null, "text only", mockModel, now, null)
        val turn = AgentContext.Turn(assistantMsg, emptyList())
        
        assertTrue(turn.tools.isEmpty())
    }
    
    // endregion
    
    // region AgentContext
    
    @Test
    fun `empty AgentContext has all null fields`() {
        val ctx = AgentContext(null, null, null, null, null)
        assertNull(ctx.compactedRounds)
        assertNull(ctx.systemPrompt)
        assertNull(ctx.historyRounds)
        assertNull(ctx.summarizedMessage)
        assertNull(ctx.currentRound)
    }
    
    @Test
    fun `AgentContext with system prompt`() {
        val ctx = AgentContext(null, "You are an assistant", null, null, null)
        assertEquals("You are an assistant", ctx.systemPrompt)
    }
    
    @Test
    fun `AgentContext with summarized message`() {
        val ctx = AgentContext(null, null, null, "previous summary", null)
        assertEquals("previous summary", ctx.summarizedMessage)
    }
    
    @Test
    fun `AgentContext with current round`() {
        val userMsg = AgentContext.Message.User("hi", null, now)
        val currentRound = AgentContext.CurrentRound(userMsg, null)
        val ctx = AgentContext(null, null, null, null, currentRound)
        
        val round = ctx.currentRound!!
        assertEquals("hi", round.userMessage.content)
    }
    
    @Test
    fun `AgentContext with history rounds`() {
        val userMsg = AgentContext.Message.User("previous", null, now)
        val round = AgentContext.CompletedRound(userMsg, null, null)
        val ctx = AgentContext(null, null, listOf(round), null, null)
        
        assertEquals(1, ctx.historyRounds!!.size)
    }
    
    @Test
    fun `AgentContext with compacted rounds`() {
        val userMsg = AgentContext.Message.User("old", null, now)
        val completedRound = AgentContext.CompletedRound(userMsg, null, null)
        val compactedRound = AgentContext.CompactedRound(now, listOf(completedRound), null)
        val ctx = AgentContext(listOf(compactedRound), null, null, null, null)
        
        val compactedList = ctx.compactedRounds!!
        assertEquals(1, compactedList.size)
        assertEquals(now, compactedList[0].compactedAt)
    }
    
    // endregion
}
