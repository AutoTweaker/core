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

import io.github.autotweaker.api.now
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.Config
import io.github.autotweaker.api.types.llm.ModelData.ModelInfo
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.agent.RuntimeProvider
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.test.TestServices
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.*

class AgentChatRequestExtTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val testUrl = "https://api.test.com/v1".toUrl()
	private val testModelInfo = ModelInfo(
		modelId = "test-model-id",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		supportsStreaming = true,
		supportsToolCalls = true,
		supportsReasoning = true,
		supportsImage = false,
		supportsJsonOutput = true,
	)
	private val testProvider = RuntimeProvider(UUID.randomUUID(), "test-provider", testUrl, "sk-test", emptyList())
	private val testModel = RuntimeModel(
		provider = testProvider,
		modelInfo = testModelInfo,
		config = Config(0.7, 2048, null, null),
		id = UUID.randomUUID()
	)
	private val agentModel = AgentModel(testModel, ReasoningEffort(false), testModel, testModel, null)
	
	private val agentId = UUID.randomUUID()
	private val messageBuilder = TestServices.messageBuilder(agentId)
	
	// toChatMessages 现为 AgentChat 的成员扩展，语言来自 i18n 服务而非入参，因此需要一个实例承载
	private val chat = AgentChat(mockk<ResilientChat>(relaxed = true), messageBuilder)
	
	private fun convert(context: RuntimeContext): List<ChatMessage> = with(chat) { context.toChatMessages() }
	
	private suspend fun userMsg(content: String = "hello") =
		messageBuilder.user(MessageContent(content = content.toContentPart()))
	
	private suspend fun assistantMsg(content: String = "response") =
		messageBuilder.assistant(
			reasoning = null,
			content = content,
			model = testModel.id,
			usage = null,
		)
	
	private suspend fun toolTurn(
		callId: String = "call-1",
		callName: String = "read",
		content: String = "file content",
	) = AgentContextIndex.Turn.Tool(
		callRef = messageBuilder.toolCall(
			timestamp = now(),
			callId = callId,
			callName = callName,
			arguments = "{}",
			reason = null,
			validatedToolName = null,
			validatedArgs = JsonPrimitive("{}"),
			resolvedRequest = null,
			presentation = null,
		).ref(),
		resultRef = messageBuilder.toolResult(
			callId = callId,
			content = content,
			data = null,
			presentation = listOf(UiBlock.Text("读取了文件")),
			status = ToolResultStatus.SUCCESS
		).ref(),
	)
	
	private fun round(
		user: AgentMessage.User,
		turns: List<AgentContextIndex.Turn>? = null,
		assistant: AgentMessage.Assistant? = null,
	) = AgentContextIndex.Round(user.ref(), turns, assistant?.ref())
	
	private fun request(
		context: RuntimeContext,
		tools: List<ChatRequest.Tool>? = null,
	) = AgentChatRequest(agentModel, tools, context)
	
	@Test
	fun `basic user message conversion`() = runTest {
		val user = userMsg("hello world")
		val ctx = RuntimeContext(null, null, null, null, round(user))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		assertEquals(1, messages.size)
		val msg = messages[0] as ChatMessage.User
		assertTrue(msg.content.merge().contains("hello world"))
		assertTrue(msg.content.merge().contains("<utc_time>"))
	}
	
	@Test
	fun `system prompt included`() = runTest {
		val user = userMsg("hello")
		val ctx = RuntimeContext("you are a helpful assistant", null, null, null, round(user))
		val req = request(context = ctx)
		
		// 系统提示现在通过 instructions 传给 provider，不再注入消息列表
		val messages = convert(req.context)
		
		assertEquals(1, messages.size)
		val userChatMsg = messages[0] as ChatMessage.User
		assertTrue(userChatMsg.content.merge().contains("hello"))
		assertFalse(userChatMsg.content.merge().contains("you are a helpful assistant"))
	}
	
	@Test
	fun `tools parameter passed through`() = runTest {
		val user = userMsg("hello")
		val ctx = RuntimeContext(null, null, null, null, round(user))
		val tool = ChatRequest.Tool("read", "read a file", Json.parseToJsonElement("{}"))
		val req = request(context = ctx, tools = listOf(tool))
		
		assertEquals(1, req.tools?.size)
		assertEquals("read", req.tools!![0].name)
	}
	
	@Test
	fun `summarized message included in user content`() = runTest {
		val user = userMsg("continue")
		val summary = messageBuilder.compact(
			content = "previous summary",
			model = UUID.randomUUID(),
			usage = null,
		)
		val compactedRounds = AgentContextIndex.CompactedRounds(
			compactedRounds = null,
			rounds = emptyList(),
			summaryMsgRef = summary.ref(),
		)
		val ctx = RuntimeContext(null, null, compactedRounds, null, round(user))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		val msg = messages[0] as ChatMessage.User
		assertTrue(msg.content.merge().contains("<summary>"))
		assertTrue(msg.content.merge().contains("previous summary"))
		assertTrue(msg.content.merge().contains("</summary>"))
		assertTrue(msg.content.merge().contains("continue"))
	}
	
	@Test
	fun `summarized message mounted on first history round when history exists`() = runTest {
		val user = userMsg("current question")
		val histUser = userMsg("previous question")
		val histAsst = assistantMsg("previous answer")
		val histRound = round(histUser, assistant = histAsst)
		val summary = messageBuilder.compact(
			content = "compacted summary of old rounds",
			model = UUID.randomUUID(),
			usage = null,
		)
		val compactedRounds = AgentContextIndex.CompactedRounds(
			compactedRounds = null,
			rounds = emptyList(),
			summaryMsgRef = summary.ref(),
		)
		val ctx = RuntimeContext(
			null, null, compactedRounds,
			historyRounds = listOf(histRound),
			currentRound = round(user),
		)
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		val histUserMsg = messages[0] as ChatMessage.User
		assertTrue(histUserMsg.content.merge().contains("<summary>"))
		assertTrue(histUserMsg.content.merge().contains("compacted summary of old rounds"))
		assertTrue(histUserMsg.content.merge().contains("previous question"))
		
		val curUserMsg = messages[2] as ChatMessage.User
		assertFalse(curUserMsg.content.merge().contains("<summary>"))
		assertTrue(curUserMsg.content.merge().contains("current question"))
	}
	
	@Test
	fun `images in user message`() = runTest {
		val img = Sha256(ByteArray(32) { it.toByte() })
		val user = messageBuilder.user(
			MessageContent(
				content = listOf(ContentPart.Text("look at this"), ContentPart.Image("image/png", img)),
			)
		)
		val ctx = RuntimeContext(null, null, null, null, round(user))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		val msg = messages[0] as ChatMessage.User
		assertTrue(msg.content.any { it is ContentPart.Image && it.data == img })
		assertTrue(msg.content.merge().contains("look at this"))
	}
	
	@Test
	fun `throws when no current round`() = runTest {
		val ctx = RuntimeContext(null, null, null, null, null)
		val req = request(context = ctx)
		
		val ex = assertFailsWith<IllegalStateException> { convert(req.context) }
		assertTrue(ex.message!!.contains("No round to send request"))
	}
	
	@Test
	fun `current round with assistant message is appended`() = runTest {
		val user = userMsg("hello")
		val asst = assistantMsg("I replied")
		val ctx = RuntimeContext(null, null, null, null, round(user, assistant = asst))
		val req = request(context = ctx)
		
		// 新模型中当前轮次可以持有已生成、等待归档的 assistant 消息，不再视为非法状态，直接在用户消息之后追加
		val messages = convert(req.context)
		
		assertEquals(2, messages.size)
		val asstChatMsg = messages[1] as ChatMessage.Assistant
		assertEquals("I replied", asstChatMsg.content)
	}
	
	@Test
	fun `current round with finished tool turn is appended`() = runTest {
		val user = userMsg("hello")
		val asst = assistantMsg("calling read")
		val turn = AgentContextIndex.Turn(asst.ref(), listOf(toolTurn()))
		val ctx = RuntimeContext(null, null, null, null, round(user, turns = listOf(turn)))
		val req = request(context = ctx)
		
		// 新模型中工具调用与响应成对存储在 Turn 中，不存在“挂起的工具调用”，转换不再抛异常
		val messages = convert(req.context)
		
		assertEquals(3, messages.size)
		val toolChatMsg = messages[2] as ChatMessage.ToolResult
		assertEquals("file content", toolChatMsg.content)
		assertEquals("call-1", toolChatMsg.id)
	}
	
	@Test
	fun `turns with tool calls included in messages`() = runTest {
		val user = userMsg("read file")
		val asst = assistantMsg("I will read it")
		val turn = AgentContextIndex.Turn(asst.ref(), listOf(toolTurn()))
		val ctx = RuntimeContext(null, null, null, null, round(user, turns = listOf(turn)))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		assertEquals(3, messages.size)
		val userChatMsg = messages[0] as ChatMessage.User
		assertTrue(userChatMsg.content.merge().contains("read file"))
		
		val asstChatMsg = messages[1] as ChatMessage.Assistant
		assertEquals("I will read it", asstChatMsg.content)
		val toolCalls = assertNotNull(asstChatMsg.toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].id)
		
		val toolChatMsg = messages[2] as ChatMessage.ToolResult
		assertEquals("file content", toolChatMsg.content)
		assertEquals("call-1", toolChatMsg.id)
	}
	
	@Test
	fun `history rounds included in messages`() = runTest {
		val user = userMsg("current question")
		val histUser = userMsg("previous question")
		val histAsst = assistantMsg("previous answer")
		val histRound = round(histUser, assistant = histAsst)
		val ctx = RuntimeContext(
			null, null, null,
			historyRounds = listOf(histRound),
			currentRound = round(user),
		)
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		assertEquals(3, messages.size)
		val histUserMsg = messages[0] as ChatMessage.User
		assertTrue(histUserMsg.content.merge().contains("previous question"))
		val histAsstMsg = messages[1] as ChatMessage.Assistant
		assertEquals("previous answer", histAsstMsg.content)
		val curUserMsg = messages[2] as ChatMessage.User
		assertTrue(curUserMsg.content.merge().contains("current question"))
	}
	
	@Test
	fun `multiple history rounds`() = runTest {
		val user = userMsg("current")
		val hist1User = userMsg("q1")
		val hist1Asst = assistantMsg("a1")
		val hist2User = userMsg("q2")
		val hist2Asst = assistantMsg("a2")
		val histRounds = listOf(
			round(hist1User, assistant = hist1Asst),
			round(hist2User, assistant = hist2Asst),
		)
		val ctx = RuntimeContext(null, null, null, histRounds, round(user))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		
		assertEquals(5, messages.size)
	}
	
	@Test
	fun `tool result as last message allows conversion`() = runTest {
		val user = userMsg("read file")
		val asst = assistantMsg("calling tool")
		val turn = AgentContextIndex.Turn(asst.ref(), listOf(toolTurn()))
		val ctx = RuntimeContext(null, null, null, null, round(user, turns = listOf(turn)))
		val req = request(context = ctx)
		
		val messages = convert(req.context)
		assertNotNull(messages)
	}
}
