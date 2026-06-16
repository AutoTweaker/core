package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class AgentChatTest {
	private val testUrl = "https://api.test.com/v1".toUrl()
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)

	private val testModelInfo = ModelInfo(
		modelId = "test-model",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		price = TokenPrice(
			inputPrice = listOf(PriceTier(0, null, testPrice)), outputPrice = listOf(PriceTier(0, null, testPrice))
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

	@AfterTest
	fun cleanup() {
		unmockkObject(ResilientChat)
	}

	private fun userMsg(content: String = "hello") =
		AgentContext.Message.User(content = MessageContent(content = content), timestamp = Clock.System.now())

	private fun ctx(user: AgentContext.Message.User) =
		AgentContext(null, null, null, null, null, AgentContext.CurrentRound(user, null))

	@Test
	fun `collects assembled message with content and finish reason`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("hello world", Clock.System.now(), null, null),
			finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}

		val user = userMsg("hello")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		assertTrue(results.any { it is AgentChatStreamResult.Assembled })

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
		assertNotNull(assembled.finishReason)
	}

	@Test
	fun `emits delta with reasoning when reasoning content arrives`() = runTest {
		val now = Clock.System.now()
		val chunkResult = ChatResult.Chunk(
			message = ChatMessage.AssistantMessage("answer", now, reasoningContent = "let me think"),
		)
		val assembledResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("answer", now, reasoningContent = "let me think"),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chunkResult, model = UUID.randomUUID()))
			emit(CoreLlmResult(assembledResult, model = UUID.randomUUID()))
		}

		val user = userMsg("question")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val delta = results.filterIsInstance<AgentChatStreamResult.Delta>().first()
		assertEquals("let me think", delta.delta.reasoningContent)
		assertEquals("answer", delta.delta.content)

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("let me think", assembled.message.reasoning)
		assertEquals("answer", assembled.message.content)
	}

	@Test
	fun `passes through deltas from multiple chunks`() = runTest {
		val now = Clock.System.now()

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(
				CoreLlmResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("hello ", now, null, null),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					ChatResult.Chunk(
						message = ChatMessage.AssistantMessage("world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage("hello world", now, null, null),
						finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
					),
					UUID.randomUUID(),
				)
			)
		}

		val user = userMsg("greet")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val deltas = results.filterIsInstance<AgentChatStreamResult.Delta>()
		assertEquals(2, deltas.size)
		assertEquals("hello ", deltas[0].delta.content)
		assertEquals("world", deltas[1].delta.content)

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
		assertNotNull(assembled.finishReason)
	}

	@Test
	fun `accumulates multiple errors and emits Failing`() = runTest {
		val now = Clock.System.now()
		val errorChatResult = ChatResult.Assembled(
			message = ChatMessage.ErrorMessage("service down", now, 503),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(errorChatResult, model = UUID.randomUUID()))
		}

		val user = userMsg("help")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val failings = results.filterIsInstance<AgentChatStreamResult.Failing>()
		assertEquals(1, failings.size)
		assertEquals(1, failings[0].errors.size)
		assertEquals("service down", failings[0].errors[0].content)
		assertEquals(503, failings[0].errors[0].statusCode)
	}

	@Test
	fun `assembled message uses correct model info for usage snapshot`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("ok", now, null, null),
			usage = Usage(100, 50, 50),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}

		val user = userMsg("test")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals(Usage(100, 50, 50), assembled.message.usageSnapshot?.usage)
	}

	@Test
	fun `assembled message with reasoning content is included`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(null, now, "thinking...", null),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(CoreLlmResult(chatResult, model = UUID.randomUUID()))
		}

		val user = userMsg("question")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("thinking...", assembled.message.reasoning)
	}

	@Test
	fun `assembled message with tool calls creates pending tool calls`() = runTest {
		val now = Clock.System.now()
		val toolCalls = listOf(
			ChatMessage.AssistantMessage.ToolCall(
				id = "call1", name = "read_file",
				arguments = """{"file":"/tmp/test"}"""
			)
		)
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage("done", now, null, toolCalls),
			finishReason = ChatResult.FinishReason("tool_calls", ChatResult.FinishReason.Type.TOOL),
		)

		mockkObject(ResilientChat)
		every {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(
				CoreLlmResult(
					ChatResult.Assembled(
						message = ChatMessage.AssistantMessage(null, now, null, null),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				CoreLlmResult(
					chatResult,
					UUID.randomUUID(),
				)
			)
		}

		val user = userMsg("read test")
		val request = AgentChatRequest(agentModel, null, ctx(user))

		val results = AgentChat.execute(request, UUID.randomUUID()).toList()

		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().last()
		assertEquals(1, assembled.toolCalls?.size)
		assertEquals("call1", assembled.toolCalls?.first()?.id)
	}
}
