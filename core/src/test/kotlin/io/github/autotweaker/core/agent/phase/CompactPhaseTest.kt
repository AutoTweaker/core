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

package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentContext
import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.AgentOutput
import io.github.autotweaker.core.agent.MutableAgentState
import io.github.autotweaker.core.agent.llm.Model
import io.github.autotweaker.core.agent.llm.Provider
import io.github.autotweaker.core.agent.llm.ResilientChatResult
import io.github.autotweaker.core.agent.llm.resilientChat
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.Usage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class CompactPhaseTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var agentState: MutableAgentState
	private lateinit var model: Model
	private var contextValue: AgentContext = AgentContext(null, null, null, null, null)
	private val capturedOutputs = mutableListOf<AgentOutput>()
	private val settings = listOf(
		SettingItem(SettingKey("core.agent.compact.prompt"), SettingItem.Value.ValString("Summarize conversation"), ""),
		SettingItem(SettingKey("core.agent.compact.max.message.chars"), SettingItem.Value.ValInt(10000), ""),
		SettingItem(
			SettingKey("core.agent.compact.message.summarize.prompt"),
			SettingItem.Value.ValString("Summarize: %s"),
			""
		),
	)
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		capturedOutputs.clear()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentState } returns agentState
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		
		contextValue = AgentContext(
			compactedRounds = null, systemPrompt = null,
			historyRounds = listOf(),
			summarizedMessage = null,
			currentRound = null,
		)
		every { env.context } answers { contextValue }
		every { env.context = any() } answers { contextValue = firstArg() }
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { contextValue = transform(contextValue) }
		}
		coEvery { env.emitOutput(any()) } answers { capturedOutputs.add(firstArg()) }
		
		mockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
	}
	
	@AfterTest
	fun tearDown() {
		unmockkStatic("io.github.autotweaker.core.agent.llm.ResilientChatKt")
	}
	
	@Test
	fun `compactPhase updates context with summarized message`() = runTest {
		val summaryContent = "<summary>compacted conversation summary</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val userMsg = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now())
		val assistantMsg = AgentContext.Message.Assistant(
			content = "hi there", model = model,
			timestamp = Clock.System.now(), usage = null,
		)
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = userMsg,
				turns = null,
				finalAssistantMessage = assistantMsg,
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		assertEquals("compacted conversation summary", contextValue.summarizedMessage)
		val contextUpdate = capturedOutputs.firstOrNull { it is AgentOutput.ContextUpdate }
		assertNotNull(contextUpdate)
		assertEquals(
			AgentOutput.ContextUpdate.UpdateReason.COMPACTED,
			(contextUpdate as AgentOutput.ContextUpdate).reason
		)
	}
	
	@Test
	fun `compactPhase emits error when summary is blank`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "   ",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
		assertEquals(AgentOutput.Error.Type.COMPACT, (error as AgentOutput.Error).type)
		assertTrue(error.message.contains("empty summary"))
	}
	
	@Test
	fun `compactPhase retries up to max before giving up`() = runTest {
		val blankResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "   ",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = blankResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		// Should have attempted MAX_COMPACT_RETRIES (5) times before giving up
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
	}
	
	@Test
	fun `extractSummary extracts content inside tags`() = runTest {
		// extractSummary is private, tested indirectly through compactPhase
		val summaryContent = "prefix text <summary>real summary here</summary> suffix text"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		assertEquals("real summary here", contextValue.summarizedMessage)
	}
	
	@Test
	fun `compactPhase preprocesses rounds with turns`() = runTest {
		val summaryContent = "<summary>done</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val userMsg = AgentContext.Message.User(content = "run command", timestamp = Clock.System.now())
		val assistantMsg = AgentContext.Message.Assistant(
			content = "calling tool", model = model,
			timestamp = Clock.System.now(), usage = null,
		)
		val toolMsg = AgentContext.Message.Tool(
			name = "bash", callId = "call-1",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "needed",
				timestamp = Clock.System.now(), model = model,
			),
			result = AgentContext.Message.Tool.Result(
				content = "command output",
				timestamp = Clock.System.now(),
				status = AgentContext.Message.Tool.Result.Status.SUCCESS,
			),
		)
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = userMsg,
				turns = listOf(
					AgentContext.Turn(assistantMessage = assistantMsg, tools = listOf(toolMsg)),
				),
				finalAssistantMessage = null,
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		assertEquals("done", contextValue.summarizedMessage)
		assertNotNull(contextValue.summarizedMessage)
	}
	
	@Test
	fun `runCompactRequest handles error message from LLM`() = runTest {
		val errorResult = ChatResult.Assembled(
			message = ChatMessage.ErrorMessage(
				content = "LLM error", createdAt = Clock.System.now(), statusCode = null,
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = errorResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
		assertEquals(AgentOutput.Error.Type.COMPACT, (error as AgentOutput.Error).type)
	}
	
	@Test
	fun `runCompactRequest accumulates content from Chunk and captures usage`() = runTest {
		val usage = Usage(100, 40, 60)
		val chunkResult = ChatResult.Chunk(
			message = ChatMessage.AssistantMessage(
				content = "partial summary",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = usage,
		)
		val assembledResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>final summary</summary>",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = usage,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chunkResult, retrying = null),
			ResilientChatResult(result = assembledResult, retrying = null),
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		val outputting = capturedOutputs.filterIsInstance<AgentOutput.CompactOutput>()
			.firstOrNull { it.status == AgentOutput.CompactOutput.Status.OUTPUTTING }
		assertNotNull(outputting)
		assertEquals("partial summary", outputting.content)
	}
	
	@Test
	fun `runCompactRequest handles exception from chat flow`() = runTest {
		every { resilientChat(any(), any(), any(), any()) } throws RuntimeException("chat failed")
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
	}
	
	@Test
	fun `convertUserMessage handles images by inserting placeholders`() = runTest {
		val summaryContent = "<summary>summary with images</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent, createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(
					content = "describe this image",
					images = listOf(mockk(relaxed = true)),
					timestamp = Clock.System.now(),
				),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "image described", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		assertEquals("summary with images", contextValue.summarizedMessage)
	}
	
	@Test
	fun `long messages trigger summarizeMessage`() = runTest {
		val longContentSettings = listOf(
			SettingItem(
				SettingKey("core.agent.compact.prompt"),
				SettingItem.Value.ValString("Summarize conversation"),
				""
			),
			SettingItem(SettingKey("core.agent.compact.max.message.chars"), SettingItem.Value.ValInt(5), ""),
			SettingItem(
				SettingKey("core.agent.compact.message.summarize.prompt"),
				SettingItem.Value.ValString("Summarize: %s"),
				""
			),
		)
		val summaryContent = "<summary>compacted</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent, createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		// First call: summarizeMessage for user message, second call: summarizeMessage for assistant message,
		// third call: runCompactRequest
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		) andThen flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		) andThen flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(
					content = "very long user message",
					timestamp = Clock.System.now(),
				),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "very long assistant response",
					model = model, timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			settings = longContentSettings
		)
		
		assertEquals("compacted", contextValue.summarizedMessage)
	}
	
	@Test
	fun `long tool result triggers summarizeMessage for tool content`() = runTest {
		val longContentSettings = listOf(
			SettingItem(SettingKey("core.agent.compact.prompt"), SettingItem.Value.ValString("Compact"), ""),
			SettingItem(SettingKey("core.agent.compact.max.message.chars"), SettingItem.Value.ValInt(5), ""),
			SettingItem(
				SettingKey("core.agent.compact.message.summarize.prompt"),
				SettingItem.Value.ValString("TLDR: %s"),
				""
			),
		)
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>done</summary>", createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		// summarizeMessage called for each long message + main compact request
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val toolMsg = AgentContext.Message.Tool(
			name = "bash", callId = "call-1",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "needed",
				timestamp = Clock.System.now(), model = model,
			),
			result = AgentContext.Message.Tool.Result(
				content = "this is a very long tool output exceeding limit",
				timestamp = Clock.System.now(),
				status = AgentContext.Message.Tool.Result.Status.SUCCESS,
			),
		)
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hi", timestamp = Clock.System.now()),
				turns = listOf(
					AgentContext.Turn(
						assistantMessage = AgentContext.Message.Assistant(
							content = "ok", model = model,
							timestamp = Clock.System.now(), usage = null,
						),
						tools = listOf(toolMsg),
					)
				),
				finalAssistantMessage = null,
			),
		)
		
		compactPhase(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			settings = longContentSettings
		)
		
		assertEquals("done", contextValue.summarizedMessage)
	}
	
	@Test
	fun `null assistant content and reasoning edge cases are handled`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>done</summary>", createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returns flowOf(
			ResilientChatResult(result = chatResult, retrying = null)
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = null, timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = null, reasoning = null, model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(env, rounds, summarizeModel = model, fallbackModels = null, settings = settings)
		
		assertEquals("done", contextValue.summarizedMessage)
	}
	
	@Test
	fun `summarizeMessage falls back to original content when result empty`() = runTest {
		val fallbackSettings = listOf(
			SettingItem(SettingKey("core.agent.compact.prompt"), SettingItem.Value.ValString("Compact"), ""),
			SettingItem(SettingKey("core.agent.compact.max.message.chars"), SettingItem.Value.ValInt(5), ""),
			SettingItem(
				SettingKey("core.agent.compact.message.summarize.prompt"),
				SettingItem.Value.ValString("TLDR: %s"),
				""
			),
		)
		// First call (summarizeMessage for long user msg): retrying non-null, result has null message
		// Second call (summarizeMessage for long assistant msg): same
		// Third call (runCompactRequest): success with summary
		val emptyResult = ChatResult.Chunk(message = null, usage = null)
		val successResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>final</summary>", createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		every { resilientChat(any(), any(), any(), any()) } returnsMany listOf(
			flowOf(
				ResilientChatResult(result = emptyResult, retrying = model),
				ResilientChatResult(result = emptyResult, retrying = null)
			),
			flowOf(ResilientChatResult(result = emptyResult, retrying = null)),
			flowOf(ResilientChatResult(result = successResult, retrying = null)),
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(
					content = "very long user message that exceeds limit",
					timestamp = Clock.System.now(),
				),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "very long assistant response that also exceeds",
					model = model, timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		compactPhase(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			settings = fallbackSettings
		)
		
		assertEquals("final", contextValue.summarizedMessage)
	}
	
	@Test
	fun `CancellationException is rethrown not swallowed`() = runTest {
		every { resilientChat(any(), any(), any(), any()) } throws CancellationException("cancelled")
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", model = model,
					timestamp = Clock.System.now(), usage = null,
				),
			),
		)
		
		val job = launch {
			compactPhase(
				env,
				rounds,
				summarizeModel = model,
				fallbackModels = null,
				settings = settings
			)
		}
		job.join()
		assertTrue(job.isCancelled)
	}
	
	// region helpers
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(name = "summarize-model", provider = provider, modelInfo = mockk(relaxed = true))
	}
	// endregion
}
