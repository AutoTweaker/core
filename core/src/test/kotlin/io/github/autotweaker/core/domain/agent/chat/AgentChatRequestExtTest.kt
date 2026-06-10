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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.api.types.llm.Price
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentChatRequestExtTest {
	
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	private val testModelInfo = ModelInfo(
		modelId = "test-model-id",
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
	private val testProvider = Provider(UUID.randomUUID(), "test-provider", testUrl, "sk-test", emptyList())
	private val testModel = Model(
		provider = testProvider,
		modelInfo = testModelInfo,
		config = Config(0.7, 2048, null, null),
		id = UUID.randomUUID()
	)
	
	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = content, timestamp = Clock.System.now())
	
	private fun assistantMsg(content: String = "response") = AgentContext.Message.Assistant(
		content = content, modelId = testModel.id, timestamp = Clock.System.now()
	)
	
	private fun toolResult() =
		AgentContext.Message.Tool(
			name = "read",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(),
				arguments = "{}",
				timestamp = Clock.System.now(),
				modelId = testModel.id, validatedArgs = null
			),
			callId = "call-1",
			result = AgentContext.Message.Tool.Result(
				content = "file content",
				timestamp = Clock.System.now(),
				status = ToolResultStatus.SUCCESS
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
		
		val messages = req.toChatMessages()
		
		assertEquals(1, messages.size)
		val msg = messages[0] as ChatMessage.UserMessage
		assertTrue(msg.content.contains("hello world"))
		assertTrue(msg.content.contains("<time>"))
		assertNull(msg.pictures)
	}
	
	@Test
	fun `system prompt included`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, "you are a helpful assistant", null, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages()
		
		assertEquals(2, messages.size)
		val sysMsg = messages[0] as ChatMessage.SystemMessage
		assertEquals("you are a helpful assistant", sysMsg.content)
		val userMsg = messages[1] as ChatMessage.UserMessage
		assertTrue(userMsg.content.contains("hello"))
	}
	
	@Test
	fun `thinking parameter passed through`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx, thinking = true)
		
		assertEquals(true, req.thinking)
	}
	
	@Test
	fun `tools parameter passed through`() {
		val user = userMsg("hello")
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val tool = ChatRequest.Tool("read", "read a file", Json.parseToJsonElement("{}"))
		val req = request(context = ctx, tools = listOf(tool))
		
		assertEquals(1, req.tools?.size)
		assertEquals("read", req.tools!![0].name)
	}
	
	@Test
	fun `summarized message included in user content`() {
		val user = userMsg("continue")
		val ctx = AgentContext(
			null,
			null,
			null,
			AgentContext.SummarizedMessage(
				id = UUID.randomUUID(),
				timestamp = Clock.System.now(),
				content = "previous summary"
			),
			currentRound(user)
		)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages()
		
		val msg = messages[0] as ChatMessage.UserMessage
		assertTrue(msg.content.contains("<summary>"))
		assertTrue(msg.content.contains("previous summary"))
		assertTrue(msg.content.contains("</summary>"))
		assertTrue(msg.content.contains("continue"))
	}
	
	@Test
	fun `summarized message mounted on first history round when history exists`() {
		val user = userMsg("current question")
		val histUser = userMsg("previous question")
		val histAsst = assistantMsg("previous answer")
		val histRound = AgentContext.CompletedRound(histUser, null, histAsst)
		val summary = AgentContext.SummarizedMessage(
			id = UUID.randomUUID(),
			timestamp = Clock.System.now(),
			content = "compacted summary of old rounds"
		)
		val ctx = AgentContext(
			null, null,
			historyRounds = listOf(histRound),
			summarizedMessage = summary,
			currentRound = currentRound(user),
		)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages()
		
		// 第一条 history UserMessage 携带摘要
		val histUserMsg = messages[0] as ChatMessage.UserMessage
		assertTrue(histUserMsg.content.contains("<summary>"))
		assertTrue(histUserMsg.content.contains("compacted summary of old rounds"))
		assertTrue(histUserMsg.content.contains("previous question"))
		
		// 当前轮次 UserMessage 不带摘要
		val curUserMsg = messages[2] as ChatMessage.UserMessage
		assertFalse(curUserMsg.content.contains("<summary>"))
		assertTrue(curUserMsg.content.contains("current question"))
	}
	
	@Test
	fun `images in user message`() {
		val img = Base64("AAAA")
		val user =
			AgentContext.Message.User(content = "look at this", images = listOf(img), timestamp = Clock.System.now())
		val ctx = AgentContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages()
		
		val msg = messages[0] as ChatMessage.UserMessage
		assertEquals(1, msg.pictures?.size)
		assertEquals(img, msg.pictures!![0])
	}
	
	@Test
	fun `throws when no current round`() {
		val ctx = AgentContext(null, null, null, null, null)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages() }
		assertTrue(ex.message!!.contains("No current round"))
	}
	
	@Test
	fun `throws when current round has assistant message set`() {
		val user = userMsg("hello")
		val asst = assistantMsg("I replied")
		val round = AgentContext.CurrentRound(user, null, asst, null)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages() }
		assertTrue(ex.message!!.contains("Last message is an assistant message"))
	}
	
	@Test
	fun `throws when pending tool calls exist`() {
		val user = userMsg("hello")
		val pending = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "id1",
				assistantMessageId = UUID.randomUUID(),
				name = "read",
				modelId = testModel.id,
				arguments = "{}",
				timestamp = Clock.System.now(), validatedArgs = null
			)
		)
		val round = AgentContext.CurrentRound(user, null, null, pending)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages() }
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
		
		val messages = req.toChatMessages()
		
		assertEquals(3, messages.size)
		val userChatMsg = messages[0] as ChatMessage.UserMessage
		assertTrue(userChatMsg.content.contains("read file"))
		
		val asstChatMsg = messages[1] as ChatMessage.AssistantMessage
		assertEquals("I will read it", asstChatMsg.content)
		val toolCalls = assertNotNull(asstChatMsg.toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].id)
		
		val toolChatMsg = messages[2] as ChatMessage.ToolMessage
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
		
		val messages = req.toChatMessages()
		
		assertEquals(3, messages.size)
		val histUserMsg = messages[0] as ChatMessage.UserMessage
		assertTrue(histUserMsg.content.contains("previous question"))
		val histAsstMsg = messages[1] as ChatMessage.AssistantMessage
		assertEquals("previous answer", histAsstMsg.content)
		val curUserMsg = messages[2] as ChatMessage.UserMessage
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
		
		val messages = req.toChatMessages()
		
		assertEquals(5, messages.size)
	}
	
	@Test
	fun `tool result as last message allows conversion`() {
		val user = userMsg("read file")
		val asst = assistantMsg("calling tool")
		val tool = toolResult()
		val turn = AgentContext.Turn(asst, listOf(tool))
		val round = AgentContext.CurrentRound(user, listOf(turn), null, null)
		val ctx = AgentContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages()
		assertNotNull(messages)
	}
}
