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

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.api.types.llm.Price
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentChatRequestExtTest {
	companion object {
		init {
			TestServices.init()
		}
	}

	private val testUrl = "https://api.test.com/v1".toUrl()
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
	private val agentModel = AgentModel(testModel, testModel, testModel, null, false)
	
	private fun userMsg(content: String = "hello") =
		RuntimeContext.Message.User(content = MessageContent(content = content), timestamp = Clock.System.now())
	
	private fun assistantMsg(content: String = "response") = RuntimeContext.Message.Assistant(
		content = content, modelId = testModel.id, timestamp = Clock.System.now()
	)
	
	private fun toolResult() =
		RuntimeContext.Message.Tool(
			call = RuntimeContext.Message.Tool.Call(
				callName = "read",
				arguments = "{}",
				timestamp = Clock.System.now(),
				validatedArgs = JsonPrimitive("{}"),
			),
			callId = "call-1",
			result = RuntimeContext.Message.Tool.Result(
				content = "file content",
				timestamp = Clock.System.now(),
				status = ToolResultStatus.SUCCESS
			),
		)
	
	private fun currentRound(userMsg: RuntimeContext.Message.User) =
		RuntimeContext.CurrentRound(userMsg, null, null, null)
	
	private fun request(
		context: RuntimeContext,
		tools: List<ChatRequest.Tool>? = null,
	) = AgentChatRequest(agentModel, tools, context)
	
	@Test
	fun `basic user message conversion`() {
		val user = userMsg("hello world")
		val ctx = RuntimeContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
		assertEquals(1, messages.size)
		val msg = messages[0] as ChatMessage.UserMessage
		assertTrue(msg.content.contains("hello world"))
		assertTrue(msg.content.contains("<utc_time>"))
		assertNull(msg.pictures)
	}
	
	@Test
	fun `system prompt included`() {
		val user = userMsg("hello")
		val ctx = RuntimeContext("you are a helpful assistant", null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
		assertEquals(2, messages.size)
		val sysMsg = messages[0] as ChatMessage.SystemMessage
		assertEquals("you are a helpful assistant", sysMsg.content)
		val userMsg = messages[1] as ChatMessage.UserMessage
		assertTrue(userMsg.content.contains("hello"))
	}
	
	@Test
	fun `tools parameter passed through`() {
		val user = userMsg("hello")
		val ctx = RuntimeContext(null, null, null, null, currentRound(user))
		val tool = ChatRequest.Tool("read", "read a file", Json.parseToJsonElement("{}"))
		val req = request(context = ctx, tools = listOf(tool))
		
		assertEquals(1, req.tools?.size)
		assertEquals("read", req.tools!![0].name)
	}
	
	@Test
	fun `summarized message included in user content`() {
		val user = userMsg("continue")
		val compactedRounds = RuntimeContext.CompactedRounds(
			compactedRounds = null,
			rounds = emptyList(),
			summarizedMessage = RuntimeContext.SummarizedMessage(
				id = UUID.randomUUID(),
				timestamp = Clock.System.now(),
				content = "previous summary"
			)
		)
		val ctx = RuntimeContext(null, null, compactedRounds, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
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
		val histRound = RuntimeContext.CompletedRound(histUser, null, histAsst)
		val compactedRounds = RuntimeContext.CompactedRounds(
			compactedRounds = null,
			rounds = emptyList(),
			summarizedMessage = RuntimeContext.SummarizedMessage(
				id = UUID.randomUUID(),
				timestamp = Clock.System.now(),
				content = "compacted summary of old rounds"
			)
		)
		val ctx = RuntimeContext(
			null, null, compactedRounds,
			historyRounds = listOf(histRound),
			currentRound = currentRound(user),
		)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
		val histUserMsg = messages[0] as ChatMessage.UserMessage
		assertTrue(histUserMsg.content.contains("<summary>"))
		assertTrue(histUserMsg.content.contains("compacted summary of old rounds"))
		assertTrue(histUserMsg.content.contains("previous question"))
		
		val curUserMsg = messages[2] as ChatMessage.UserMessage
		assertFalse(curUserMsg.content.contains("<summary>"))
		assertTrue(curUserMsg.content.contains("current question"))
	}
	
	@Test
	fun `images in user message`() {
		val img = Sha256(ByteArray(32) { it.toByte() })
		val user = RuntimeContext.Message.User(
			content = MessageContent(content = "look at this", images = listOf(img)),
			timestamp = Clock.System.now()
		)
		val ctx = RuntimeContext(null, null, null, null, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
		val msg = messages[0] as ChatMessage.UserMessage
		assertEquals(1, msg.pictures?.size)
		assertContentEquals(img.bytes, msg.pictures!![0].bytes)
	}
	
	@Test
	fun `throws when no current round`() {
		val ctx = RuntimeContext(null, null, null, null, null)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages(Locale.ENGLISH) }
		assertTrue(ex.message!!.contains("No current round"))
	}
	
	@Test
	fun `throws when current round has assistant message set`() {
		val user = userMsg("hello")
		val asst = assistantMsg("I replied")
		val round = RuntimeContext.CurrentRound(user, null, asst, null)
		val ctx = RuntimeContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages(Locale.ENGLISH) }
		assertTrue(ex.message!!.contains("Last message is an assistant message"))
	}
	
	@Test
	fun `throws when pending tool calls exist`() {
		val user = userMsg("hello")
		val pending = listOf(
			RuntimeContext.CurrentRound.PendingToolCall(
				callId = "id1",
				callName = "read",
				arguments = "{}",
				reason = "test",
				timestamp = Clock.System.now(),
				validatedToolName = "read",
				validatedArgs = JsonPrimitive("{}"),
			)
		)
		val round = RuntimeContext.CurrentRound(user, null, null, pending)
		val ctx = RuntimeContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { req.toChatMessages(Locale.ENGLISH) }
		assertTrue(ex.message!!.contains("Pending tool calls exist"))
	}
	
	@Test
	fun `turns with tool calls included in messages`() {
		val user = userMsg("read file")
		val asst = assistantMsg("I will read it")
		val tool = toolResult()
		val turn = RuntimeContext.Turn(asst, listOf(tool))
		val round = RuntimeContext.CurrentRound(user, listOf(turn), null, null)
		val ctx = RuntimeContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
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
		val histRound = RuntimeContext.CompletedRound(histUser, null, histAsst)
		val ctx = RuntimeContext(
			null, null, null,
			historyRounds = listOf(histRound),
			currentRound = currentRound(user),
		)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
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
			RuntimeContext.CompletedRound(hist1User, null, hist1Asst),
			RuntimeContext.CompletedRound(hist2User, null, hist2Asst),
		)
		val ctx = RuntimeContext(null, null, null, histRounds, currentRound(user))
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		
		assertEquals(5, messages.size)
	}
	
	@Test
	fun `tool result as last message allows conversion`() {
		val user = userMsg("read file")
		val asst = assistantMsg("calling tool")
		val tool = toolResult()
		val turn = RuntimeContext.Turn(asst, listOf(tool))
		val round = RuntimeContext.CurrentRound(user, listOf(turn), null, null)
		val ctx = RuntimeContext(null, null, null, null, round)
		val req = request(context = ctx)
		
		val messages = req.toChatMessages(Locale.ENGLISH)
		assertNotNull(messages)
	}
}
