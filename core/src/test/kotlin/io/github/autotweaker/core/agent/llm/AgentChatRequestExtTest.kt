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
import io.github.autotweaker.core.llm.ChatRequest
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentChatRequestExtTest {
	
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
	
	private fun userMsg(content: String = "hello") = AgentContext.Message.User(content, null, Clock.System.now())
	
	private fun assistantMsg(content: String = "response") = AgentContext.Message.Assistant(
		null, content, testModel, Clock.System.now(), null
	)
	
	private fun toolResult() =
		AgentContext.Message.Tool(
			name = "read",
			call = AgentContext.Message.Tool.Call("{}", null, Clock.System.now(), testModel),
			callId = "call-1",
			result = AgentContext.Message.Tool.Result(
				"file content", Clock.System.now(), AgentContext.Message.Tool.Result.Status.SUCCESS
			),
		)
	
	private fun currentRound(userMsg: AgentContext.Message.User) =
		AgentContext.CurrentRound(userMsg, null, null, null)
	
	private fun request(
		model: Model = testModel,
		context: AgentContext,
		thinking: Boolean? = null,
		tools: List<ChatRequest.Tool>? = null,
	) = AgentChatRequest(model, null, thinking, tools, context)
	
	@Test
	fun `basic user message conversion`() {
		val user = userMsg("hello world")
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		assertEquals("test-model", chatReq.model)
		assertEquals(1, chatReq.messages.size)
		val msg = chatReq.messages[0] as ChatMessage.UserMessage
		assertTrue(msg.content.contains("hello world"))
		assertTrue(msg.content.contains("<time>"))
		assertNull(msg.pictures)
	}
	
	@Test
	fun `system prompt included`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, "you are a helpful assistant", null, null, currentRound(user))
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		assertEquals(2, chatReq.messages.size)
		val sysMsg = chatReq.messages[0] as ChatMessage.SystemMessage
		assertEquals("you are a helpful assistant", sysMsg.content)
		val userMsg = chatReq.messages[1] as ChatMessage.UserMessage
		assertTrue(userMsg.content.contains("hello"))
	}
	
	@Test
	fun `thinking parameter passed through`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx, thinking = true)
		
		val chatReq = req.toChatRequest()
		
		assertEquals(true, chatReq.thinking)
	}
	
	@Test
	fun `tools parameter passed through`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val tool = ChatRequest.Tool("read", "read a file", Json.parseToJsonElement("{}"))
		val req = request(context = ctx, tools = listOf(tool))
		
		val chatReq = req.toChatRequest()
		
		assertEquals(1, chatReq.tools?.size)
		assertEquals("read", chatReq.tools!![0].name)
	}
	
	@Test
	fun `summarized message included in user content`() {
		val user = userMsg("continue")
		val ctx = AgentContext(null, null, null, "previous summary", currentRound(user))
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		val msg = chatReq.messages[0] as ChatMessage.UserMessage
		assertTrue(msg.content.contains("<summary>"))
		assertTrue(msg.content.contains("previous summary"))
		assertTrue(msg.content.contains("</summary>"))
		assertTrue(msg.content.contains("continue"))
	}
	
	@Test
	fun `images in user message`() {
		val img = io.github.autotweaker.core.Base64("AAAA")
		val user = AgentContext.Message.User("look at this", listOf(img), Clock.System.now())
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		val msg = chatReq.messages[0] as ChatMessage.UserMessage
		assertEquals(1, msg.pictures?.size)
		assertEquals(img, msg.pictures!![0])
	}
	
	@Test
	fun `throws when no current round`() {
		val ctx = AgentContext(null, null, null, null, null)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatRequest() }
		assertTrue(ex.message!!.contains("No current round"))
	}
	
	@Test
	fun `throws when current round has assistant message set`() {
		val user = userMsg("hello")
		val asst = assistantMsg("I replied")
		val round = AgentContext.CurrentRound(user, null, asst, null)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatRequest() }
		assertTrue(ex.message!!.contains("Last message is an assistant message"))
	}
	
	@Test
	fun `throws when pending tool calls exist`() {
		val user = userMsg("hello")
		val pending = listOf(
			AgentContext.CurrentRound.PendingToolCall("id1", "read", testModel, "{}", null, Clock.System.now())
		)
		val round = AgentContext.CurrentRound(user, null, null, pending)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatRequest() }
		assertTrue(ex.message!!.contains("Pending tool calls exist"))
	}
	
	@Test
	fun `turns with tool calls included in messages`() {
		val user = userMsg("read file")
		val asst = assistantMsg("I will read it")
		val tool = toolResult()
		val turn = AgentContext.Turn(asst, listOf(tool))
		val round = AgentContext.CurrentRound(user, listOf(turn), null, null)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		assertEquals(3, chatReq.messages.size)
		val userChatMsg = chatReq.messages[0] as ChatMessage.UserMessage
		assertTrue(userChatMsg.content.contains("read file"))
		
		val asstChatMsg = chatReq.messages[1] as ChatMessage.AssistantMessage
		assertEquals("I will read it", asstChatMsg.content)
		val toolCalls = assertNotNull(asstChatMsg.toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].id)
		
		val toolChatMsg = chatReq.messages[2] as ChatMessage.ToolMessage
		assertEquals("file content", toolChatMsg.content)
		assertEquals("call-1", toolChatMsg.toolCallId)
	}
	
	@Test
	fun `history rounds included in messages`() {
		val user = userMsg("current question")
		val histUser = userMsg("previous question")
		val histAsst = assistantMsg("previous answer")
		val histRound = AgentContext.CompletedRound(histUser, null, histAsst)
		val ctx = AgentContext(
			null, null,
			historyRounds = listOf(histRound),
			summarizedMessage = null,
			currentRound = currentRound(user),
		)
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		assertEquals(3, chatReq.messages.size)
		val histUserMsg = chatReq.messages[0] as ChatMessage.UserMessage
		assertTrue(histUserMsg.content.contains("previous question"))
		val histAsstMsg = chatReq.messages[1] as ChatMessage.AssistantMessage
		assertEquals("previous answer", histAsstMsg.content)
		val curUserMsg = chatReq.messages[2] as ChatMessage.UserMessage
		assertTrue(curUserMsg.content.contains("current question"))
	}
	
	@Test
	fun `multiple history rounds`() {
		val user = userMsg("current")
		val hist1User = userMsg("q1")
		val hist1Asst = assistantMsg("a1")
		val hist2User = userMsg("q2")
		val hist2Asst = assistantMsg("a2")
		val histRounds = listOf(
			AgentContext.CompletedRound(hist1User, null, hist1Asst),
			AgentContext.CompletedRound(hist2User, null, hist2Asst),
		)
		val ctx = AgentContext(null, null, histRounds, null, currentRound(user))
		val req = request(context = ctx)
		
		val chatReq = req.toChatRequest()
		
		assertEquals(5, chatReq.messages.size)
	}
	
	@Test
	fun `tool result as last message allows conversion`() {
		val user = userMsg("read file")
		val asst = assistantMsg("calling tool")
		val tool = toolResult()
		val turn = AgentContext.Turn(asst, listOf(tool))
		// assistantMessage is null, turns exist with tool result last
		val round = AgentContext.CurrentRound(user, listOf(turn), null, null)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		// should not throw because last message is Tool, not Assistant
		val chatReq = req.toChatRequest()
		assertNotNull(chatReq)
	}
}
