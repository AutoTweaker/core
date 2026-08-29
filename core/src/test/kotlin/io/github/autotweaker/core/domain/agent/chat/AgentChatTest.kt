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

import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.Config
import io.github.autotweaker.api.types.llm.ModelData.ModelInfo
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.AgentModel
import io.github.autotweaker.core.domain.agent.RuntimeContext
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.agent.RuntimeProvider
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentChatTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val testUrl = "https://api.test.com/v1".toUrl()
	
	private val testModelInfo = ModelInfo(
		modelId = "test-model",
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

	private fun userMsg(content: String = "hello") =
		RuntimeContext.Message.User(
			id = UUID.randomUUID(),
			content = MessageContent(content = content.toContentPart()),
			timestamp = Clock.System.now()
		)
	
	private fun ctx(user: RuntimeContext.Message.User) =
		RuntimeContext(null, null, null, null, RuntimeContext.CurrentRound(user, null, null, null, null))
	
	@Test
	fun `collects assembled message with content and finish reason`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("hello world", Clock.System.now(), null, null),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("hello")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		assertTrue(results.any { it is AgentChatStreamResult.Assembled })
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
	}
	
	@Test
	fun `emits delta with reasoning when reasoning content arrives`() = runTest {
		val now = Clock.System.now()
		val chunkResult = ChatResult.Chunk(
			content = "answer",
			reasoningContent = "let me think",
			toolCalls = null,
		)
		val assembledResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("answer", now, reasoningContent = "let me think"),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chunkResult, model = UUID.randomUUID()))
			emit(LlmResult(assembledResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("question")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
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
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(
				LlmResult(
					ChatResult.Chunk(
						content = "hello ",
						reasoningContent = null,
						toolCalls = null,
					),
					UUID.randomUUID(),
				)
			)
			emit(
				LlmResult(
					ChatResult.Chunk(
						content = "world",
						reasoningContent = null,
						toolCalls = null,
					),
					UUID.randomUUID(),
				)
			)
			emit(
				LlmResult(
					ChatResult.Assembled(
						message = ChatMessage.Assistant("hello world", now, null, null),
					),
					UUID.randomUUID(),
				)
			)
		}
		
		val user = userMsg("greet")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		val deltas = results.filterIsInstance<AgentChatStreamResult.Delta>()
		assertEquals(2, deltas.size)
		assertEquals("hello ", deltas[0].delta.content)
		assertEquals("world", deltas[1].delta.content)
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
	}
	
	@Test
	fun `emits Failing for error message`() = runTest {
		val errorChatResult = ChatResult.Failed("service down", 503)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(errorChatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("help")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		val failings = results.filterIsInstance<AgentChatStreamResult.Failing>()
		assertEquals(1, failings.size)
		assertEquals("service down", failings[0].error)
		assertEquals(503, failings[0].statusCode)
	}
	
	@Test
	fun `assembled message carries usage`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("ok", now, null, null),
			usage = Usage(100, 50, 50),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("test")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals(Usage(100, 50, 50), assembled.message.usage)
	}
	
	@Test
	fun `assembled message with reasoning content is included`() = runTest {
		val now = Clock.System.now()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant(null, now, "thinking...", null),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val user = userMsg("question")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().first()
		assertEquals("thinking...", assembled.message.reasoning)
	}
	
	@Test
	fun `assembled message with tool calls creates pending tool calls`() = runTest {
		val now = Clock.System.now()
		val toolCalls = listOf(
			ChatMessage.Assistant.ToolCall(
				id = "call1", name = "read_file",
				arguments = """{"file":"/tmp/test"}"""
			)
		)
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("done", now, null, toolCalls),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(
				LlmResult(
					ChatResult.Assembled(
						message = ChatMessage.Assistant(null, now, null, null),
					),
					UUID.randomUUID(),
				)
			)
			emit(
				LlmResult(
					chatResult,
					UUID.randomUUID(),
				)
			)
		}
		
		val user = userMsg("read test")
		val request = AgentChatRequest(agentModel, null, ctx(user))
		
		val results = AgentChat(chat).execute(request, UUID.randomUUID()).toList()
		
		val assembled = results.filterIsInstance<AgentChatStreamResult.Assembled>().last()
		assertEquals(1, assembled.toolCalls?.size)
		assertEquals("call1", assembled.toolCalls?.first()?.id)
	}
}
