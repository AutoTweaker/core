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
import io.github.autotweaker.api.types.agent.AgentContextIndex
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.Config
import io.github.autotweaker.api.types.llm.ModelData.ModelInfo
import io.github.autotweaker.api.types.message.MessageContent
import io.github.autotweaker.api.types.message.ref
import io.github.autotweaker.core.domain.agent.*
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.test.TestServices
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
	
	/**
	 * 构造一个仅包含当前轮次的请求，用户消息经 [MessageBuilder] 落盘后以 ref 索引。
	 *
	 * @return 请求，以及供 [AgentChat] 写入响应消息的 [MessageBuilder]（须与请求同属一个 agent）。
	 */
	private suspend fun request(
		agentId: UUID,
		content: String = "hello",
	): Pair<AgentChatRequest, MessageBuilder> {
		val msg = TestServices.messageBuilder(agentId)
		val user = msg.user(MessageContent(content = content.toContentPart()))
		val context = RuntimeContext(
			systemPrompt = null,
			injections = null,
			compactedRounds = null,
			historyRounds = null,
			currentRound = AgentContextIndex.Round(
				userMsgRef = user.ref(),
				turns = null,
				assistantMsgRef = null
			),
		)
		return AgentChatRequest(agentModel, null, context) to msg
	}
	
	@Test
	fun `collects assembled message with content and finish reason`() = runTest {
		val agentId = UUID.randomUUID()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("hello world"),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val (req, msg) = request(agentId, content = "hello")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		assertTrue(results.any { it is AgentChatResult.Assembled })
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
	}
	
	@Test
	fun `emits delta with reasoning when reasoning content arrives`() = runTest {
		val agentId = UUID.randomUUID()
		val chunkResult = ChatResult.Chunk(
			content = "answer",
			reasoningContent = "let me think",
			toolCalls = null,
		)
		val assembledResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("answer", reasoningContent = "let me think"),
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
		
		val (req, msg) = request(agentId, content = "question")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val delta = results.filterIsInstance<AgentChatResult.Delta>().first()
		assertEquals("let me think", delta.delta.reasoningContent)
		assertEquals("answer", delta.delta.content)
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().first()
		assertEquals("let me think", assembled.message.reasoning)
		assertEquals("answer", assembled.message.content)
	}
	
	@Test
	fun `passes through deltas from multiple chunks`() = runTest {
		val agentId = UUID.randomUUID()
		
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
						message = ChatMessage.Assistant("hello world"),
					),
					UUID.randomUUID(),
				)
			)
		}
		
		val (req, msg) = request(agentId, content = "greet")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val deltas = results.filterIsInstance<AgentChatResult.Delta>()
		assertEquals(2, deltas.size)
		assertEquals("hello ", deltas[0].delta.content)
		assertEquals("world", deltas[1].delta.content)
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().first()
		assertEquals("hello world", assembled.message.content)
	}
	
	@Test
	fun `emits Failing for error message`() = runTest {
		val agentId = UUID.randomUUID()
		val errorChatResult = ChatResult.Failed("service down", 503)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(errorChatResult, model = UUID.randomUUID()))
		}
		
		val (req, msg) = request(agentId, content = "help")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val failings = results.filterIsInstance<AgentChatResult.Failing>()
		assertEquals(1, failings.size)
		assertEquals("service down", failings[0].error)
		assertEquals(503, failings[0].statusCode)
	}
	
	@Test
	fun `assembled message carries usage`() = runTest {
		val agentId = UUID.randomUUID()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("ok"),
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
		
		val (req, msg) = request(agentId, content = "test")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().first()
		assertEquals(Usage(100, 50, 50), assembled.message.usage)
	}
	
	@Test
	fun `assembled message with reasoning content is included`() = runTest {
		val agentId = UUID.randomUUID()
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant(null, reasoningContent = "thinking..."),
		)
		
		val chat = mockk<ResilientChat>()
		every {
			chat.execute(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
			)
		} returns flow {
			emit(LlmResult(chatResult, model = UUID.randomUUID()))
		}
		
		val (req, msg) = request(agentId, content = "question")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().first()
		assertEquals("thinking...", assembled.message.reasoning)
	}
	
	@Test
	fun `assembled message with tool calls creates pending tool calls`() = runTest {
		val agentId = UUID.randomUUID()
		val toolCalls = listOf(
			ChatMessage.Assistant.ToolCall(
				id = "call1", name = "read_file",
				arguments = """{"file":"/tmp/test"}"""
			)
		)
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.Assistant("done", toolCalls = toolCalls),
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
						message = ChatMessage.Assistant(null),
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
		
		val (req, msg) = request(agentId, content = "read test")
		
		val results = AgentChat(chat, msg).execute(req, agentId).toList()
		
		val assembled = results.filterIsInstance<AgentChatResult.Assembled>().last()
		assertEquals(1, assembled.toolCalls?.size)
		assertEquals("call1", assembled.toolCalls?.first()?.id)
	}
}
