package io.github.autotweaker.core.domain.agent

import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentContextTest {

	private val mockModelId: UUID = UUID.randomUUID()
	private val now: kotlin.time.Instant = Clock.System.now()

	@Test
	fun `User message with content`() {
		val msg = AgentContext.Message.User(content = MessageContent(content = "hello"), timestamp = now)
		assertEquals("hello", msg.content.content)
		assertNull(msg.content.images)
		assertEquals(now, msg.timestamp)
	}

	@Test
	fun `User message with images`() {
		val img = Base64("aaaa")
		val msg = AgentContext.Message.User(
			content = MessageContent(content = "hello", images = listOf(img)),
			timestamp = now
		)
		assertEquals(listOf(img), msg.content.images)
	}

	@Test
	fun `User message with empty content`() {
		val msg = AgentContext.Message.User(content = MessageContent(content = ""), timestamp = now)
		assertEquals("", msg.content.content)
	}

	@Test
	fun `Assistant message with content`() {
		val msg = AgentContext.Message.Assistant(content = "answer", modelId = mockModelId, timestamp = now)
		assertEquals("answer", msg.content)
		assertNull(msg.reasoning)
		assertEquals(mockModelId, msg.modelId)
		assertNull(msg.usageSnapshot)
	}

	@Test
	fun `Assistant message with reasoning`() {
		val msg = AgentContext.Message.Assistant(
			reasoning = "thinking",
			content = "answer",
			modelId = mockModelId,
			timestamp = now,
			usageSnapshot = UsageSnapshot(usage = Usage(5, 5), model = mockk())
		)
		assertEquals("thinking", msg.reasoning)
		assertEquals("answer", msg.content)
		assertEquals(Usage(5, 5), msg.usageSnapshot?.usage)
	}

	@Test
	fun `Assistant message with null content and reasoning`() {
		val msg = AgentContext.Message.Assistant(modelId = mockModelId, timestamp = now)
		assertNull(msg.content)
		assertNull(msg.reasoning)
	}

	@Test
	fun `Tool message with success result`() {
		val call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(),
			arguments = """{"cmd":"ls"}""",
			reason = "test",
			timestamp = now,
			validatedArgs = JsonPrimitive("{}"),
		)
		val result = AgentContext.Message.Tool.Result(
			content = "output", timestamp = now, status = ToolResultStatus.SUCCESS
		)
		val msg = AgentContext.Message.Tool(name = "bash", call = call, callId = "call-1", result = result)

		assertEquals("bash", msg.name)
		assertEquals("call-1", msg.callId)
		assertEquals("""{"cmd":"ls"}""", msg.call.arguments)
		assertEquals("test", msg.call.reason)
		assertEquals("output", msg.result.content)
		assertEquals(ToolResultStatus.SUCCESS, msg.result.status)
	}

	@Test
	fun `Tool message with failure status`() {
		val call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(),
			arguments = "{}",
			timestamp = now,
			validatedArgs = JsonPrimitive("{}"),
		)
		val result = AgentContext.Message.Tool.Result(
			content = "error", timestamp = now, status = ToolResultStatus.FAILURE
		)
		val msg = AgentContext.Message.Tool(name = "read", call = call, callId = "call-2", result = result)

		assertEquals("read", msg.name)
		assertEquals(ToolResultStatus.FAILURE, msg.result.status)
	}

	@Test
	fun `Tool message with timeout status`() {
		val result = AgentContext.Message.Tool.Result(
			content = "timeout", timestamp = now, status = ToolResultStatus.TIMEOUT
		)
		assertEquals(ToolResultStatus.TIMEOUT, result.status)
	}

	@Test
	fun `Tool message with cancelled status`() {
		val result = AgentContext.Message.Tool.Result(
			content = "cancelled", timestamp = now, status = ToolResultStatus.CANCELLED
		)
		assertEquals(ToolResultStatus.CANCELLED, result.status)
	}

	@Test
	fun `Tool call with null reason`() {
		val call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(),
			arguments = "{}",
			timestamp = now,
			validatedArgs = JsonPrimitive("{}"),
		)
		assertNull(call.reason)
	}

	@Test
	fun `CompletedRound with turns and final message`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "hello"), timestamp = now)
		val assistantMsg = AgentContext.Message.Assistant(content = "hi", modelId = mockModelId, timestamp = now)
		val turn = AgentContext.Turn(assistantMsg, emptyList())
		val round = AgentContext.CompletedRound(userMsg, listOf(turn), assistantMsg)

		assertEquals(userMsg, round.userMessage)
		assertEquals(1, round.turns!!.size)
		assertEquals(assistantMsg, round.finalAssistantMessage)
	}

	@Test
	fun `CompletedRound with null turns and final message`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "hello"), timestamp = now)
		val round = AgentContext.CompletedRound(userMsg, null, null)

		assertNull(round.turns)
		assertNull(round.finalAssistantMessage)
	}

	@Test
	fun `CurrentRound with user message only`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "hello"), timestamp = now)
		val round = AgentContext.CurrentRound(userMsg, null)

		assertEquals(userMsg, round.userMessage)
		assertNull(round.turns)
		assertNull(round.assistantMessage)
		assertNull(round.pendingToolCalls)
	}

	@Test
	fun `CurrentRound with pending tool calls`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "read file"), timestamp = now)
		val pending = listOf(
			AgentContext.CurrentRound.PendingToolCall(
				callId = "c1", name = "read_file",
				arguments = """{"path":"/tmp"}""", reason = "need to read", timestamp = now,
				validatedArgs = JsonPrimitive("{}"),
			)
		)
		val round = AgentContext.CurrentRound(userMsg, null, null, pending)

		val calls = round.pendingToolCalls!!
		assertEquals(1, calls.size)
		assertEquals("c1", calls[0].callId)
		assertEquals("read_file", calls[0].name)
		assertEquals("""{"path":"/tmp"}""", calls[0].arguments)
		assertEquals("need to read", calls[0].reason)
	}

	@Test
	fun `PendingToolCall required fields`() {
		val pending = AgentContext.CurrentRound.PendingToolCall(
			callId = "c1",
			name = "bash_run",
			arguments = "{}",
			reason = "test",
			timestamp = now,
			validatedArgs = JsonPrimitive("{}"),
		)
		assertEquals("test", pending.reason)
	}

	@Test
	fun `Turn holds assistant message and tools`() {
		val assistantMsg = AgentContext.Message.Assistant(content = "done", modelId = mockModelId, timestamp = now)
		val call = AgentContext.Message.Tool.Call(
			assistantMessageId = UUID.randomUUID(),
			arguments = "{}",
			timestamp = now,
			validatedArgs = JsonPrimitive("{}"),
		)
		val result = AgentContext.Message.Tool.Result(
			content = "ok",
			timestamp = now,
			status = ToolResultStatus.SUCCESS
		)
		val toolMsg = AgentContext.Message.Tool(name = "bash", call = call, callId = "c1", result = result)
		val turn = AgentContext.Turn(assistantMsg, listOf(toolMsg))

		assertEquals(assistantMsg, turn.assistantMessage)
		assertEquals(1, turn.tools.size)
		assertEquals("bash", turn.tools[0].name)
	}

	@Test
	fun `Turn with empty tools`() {
		val assistantMsg = AgentContext.Message.Assistant(content = "text only", modelId = mockModelId, timestamp = now)
		val turn = AgentContext.Turn(assistantMsg, emptyList())

		assertTrue(turn.tools.isEmpty())
	}

	@Test
	fun `empty AgentContext has all null fields`() {
		val ctx = AgentContext(null, null, null, null, null, null)
		assertNull(ctx.compactedRounds)
		assertNull(ctx.systemPrompt)
		assertNull(ctx.historyRounds)
		assertNull(ctx.summarizedMessage)
		assertNull(ctx.currentRound)
	}

	@Test
	fun `AgentContext with system prompt`() {
		val ctx = AgentContext(null, "You are an assistant", null, null, null, null)
		assertEquals("You are an assistant", ctx.systemPrompt)
	}

	@Test
	fun `AgentContext with summarized message`() {
		val ctx = AgentContext(
			null, null, null, null,
			AgentContext.SummarizedMessage(id = UUID.randomUUID(), timestamp = now, content = "previous summary"),
			null
		)
		assertEquals("previous summary", ctx.summarizedMessage?.content)
	}

	@Test
	fun `AgentContext with current round`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "hi"), timestamp = now)
		val currentRound = AgentContext.CurrentRound(userMsg, null)
		val ctx = AgentContext(null, null, null, null, null, currentRound)

		val round = ctx.currentRound!!
		assertEquals("hi", round.userMessage.content.content)
	}

	@Test
	fun `AgentContext with history rounds`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "previous"), timestamp = now)
		val round = AgentContext.CompletedRound(userMsg, null, null)
		val ctx = AgentContext(null, null, null, listOf(round), null, null)

		assertEquals(1, ctx.historyRounds!!.size)
	}

	@Test
	fun `AgentContext with compacted rounds`() {
		val userMsg = AgentContext.Message.User(content = MessageContent(content = "old"), timestamp = now)
		val completedRound = AgentContext.CompletedRound(userMsg, null, null)
		val compactedRound = AgentContext.CompactedRound(
			rounds = listOf(completedRound),
			summarizedMessage = AgentContext.SummarizedMessage(
				id = UUID.randomUUID(),
				timestamp = now,
				content = "summary"
			)
		)
		val ctx = AgentContext(listOf(compactedRound), null, null, null, null, null)

		val compactedList = ctx.compactedRounds!!
		assertEquals(1, compactedList.size)
		assertEquals(now, compactedList[0].summarizedMessage.timestamp)
	}
}
