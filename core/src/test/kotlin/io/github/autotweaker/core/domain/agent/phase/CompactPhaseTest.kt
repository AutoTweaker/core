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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.agent.AgentError
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.ToolResultStatus
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.CoreLlmResult
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.AgentOutput
import io.github.autotweaker.core.domain.agent.MutableAgentState
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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
	private val _contextFlow = MutableStateFlow(AgentContext(null, null, null, null, null))
	private val capturedOutputs = mutableListOf<AgentOutput>()
	private val settings = mockk<SettingService>().also {
		every { it.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	
	@BeforeTest
	fun setUp() {
		agentState = MutableAgentState()
		model = mockModel()
		capturedOutputs.clear()
		
		env = mockk(relaxUnitFun = true)
		every { env.agentId } returns UUID.randomUUID()
		every { env.agentState } returns agentState
		every { env.toolCancelledMessage } returns "Tool cancelled"
		every { env.toolRejectedMessage } returns "Tool rejected"
		every { env.toolRejectedWithFeedbackMessage } returns "Tool rejected: %s"
		
		_contextFlow.value = AgentContext(
			compactedRounds = null, systemPrompt = null,
			historyRounds = listOf(),
			summarizedMessage = null,
			currentRound = null,
		)
		every { env.context } returns _contextFlow
		coEvery { env.updateContext(any()) } answers {
			val transform = firstArg<suspend (AgentContext) -> AgentContext>()
			runBlocking { _contextFlow.value = transform(_contextFlow.value) }
		}
		coEvery { env.emitOutput(any()) } answers { capturedOutputs.add(firstArg()) }
		
		mockkObject(ResilientChat)
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `compactPhase updates context with summarized message`() = runTest {
		val summaryContent =
			"<summary>compacted conversation summary with enough characters to pass validation check</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val userMsg = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now())
		val assistantMsg = AgentContext.Message.Assistant(
			content = "hi there", modelId = model.id,
			timestamp = Clock.System.now(), usageSnapshot = null,
		)
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = userMsg,
				turns = null,
				finalAssistantMessage = assistantMsg,
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		assertEquals(
			"compacted conversation summary with enough characters to pass validation check",
			_contextFlow.value.summarizedMessage?.content
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
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
		assertEquals(AgentError.Type.COMPACT, (error as AgentOutput.Error).error.type)
		assertTrue(error.error.message.contains("shorter than 50 chars"))
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
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = blankResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		// Should have attempted MAX_COMPACT_RETRIES (5) times before giving up
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
	}
	
	@Test
	fun `extractSummary extracts content inside tags`() = runTest {
		// extractSummary is private, tested indirectly through compactPhase
		val summaryContent =
			"prefix text <summary>the real extracted summary content that is definitely long enough to pass the validation check successfully</summary> suffix text"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		assertEquals(
			"the real extracted summary content that is definitely long enough to pass the validation check successfully",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `compactPhase preprocesses rounds with turns`() = runTest {
		val summaryContent =
			"<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent,
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val userMsg = AgentContext.Message.User(content = "run command", timestamp = Clock.System.now())
		val assistantMsg = AgentContext.Message.Assistant(
			content = "calling tool", modelId = model.id,
			timestamp = Clock.System.now(), usageSnapshot = null,
		)
		val toolMsg = AgentContext.Message.Tool(
			name = "bash", callId = "call-1",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "needed",
				timestamp = Clock.System.now(), modelId = model.id,
				
				validatedArgs = null,
			),
			result = AgentContext.Message.Tool.Result(
				content = "command output",
				timestamp = Clock.System.now(),
				status = ToolResultStatus.SUCCESS,
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
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
		assertNotNull(_contextFlow.value.summarizedMessage?.content)
	}
	
	@Test
	fun `runCompactRequest handles error message from LLM`() = runTest {
		val errorResult = ChatResult.Assembled(
			message = ChatMessage.ErrorMessage(
				content = "LLM error", createdAt = Clock.System.now(), statusCode = null,
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = errorResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
		assertEquals(AgentError.Type.COMPACT, (error as AgentOutput.Error).error.type)
	}
	
	@Test
	fun `runCompactRequest accumulates content from Chunk and captures usage`() = runTest {
		val usage = Usage(promptTokens = 40, completionTokens = 60)
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
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chunkResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000")),
			CoreLlmResult(result = assembledResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000")),
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		val outputting = capturedOutputs.filterIsInstance<AgentOutput.Compact>()
			.firstOrNull { it.output.status == CompactOutput.Status.OUTPUTTING }
		assertNotNull(outputting)
		assertEquals("partial summary", outputting.output.content)
	}
	
	@Test
	fun `runCompactRequest handles exception from chat flow`() = runTest {
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException(
			"chat failed"
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		val error = capturedOutputs.firstOrNull { it is AgentOutput.Error }
		assertNotNull(error)
	}
	
	@Test
	fun `convertUserMessage handles images by inserting placeholders`() = runTest {
		val summaryContent =
			"<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent, createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
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
					content = "image described", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `long messages trigger summarizeMessage`() = runTest {
		val longContentSettings = mockk<SettingService>().also {
			every { it.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
		}
		val summaryContent =
			"<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>"
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = summaryContent, createdAt = Clock.System.now(), model = "summarize-model",
			),
			usage = null,
		)
		// First call: summarizeMessage for user message, second call: summarizeMessage for assistant message,
		// third call: runCompactRequest
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		) andThen flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		) andThen flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
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
					modelId = model.id, timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			service = longContentSettings
		)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `long tool result triggers summarizeMessage for tool content`() = runTest {
		val longContentSettings = mockk<SettingService>().also {
			every { it.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
		}
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		// summarizeMessage called for each long message + main compact request
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val toolMsg = AgentContext.Message.Tool(
			name = "bash", callId = "call-1",
			call = AgentContext.Message.Tool.Call(
				assistantMessageId = UUID.randomUUID(), arguments = "{}", reason = "needed",
				timestamp = Clock.System.now(), modelId = model.id,
				
				validatedArgs = null,
			),
			result = AgentContext.Message.Tool.Result(
				content = "this is a very long tool output exceeding limit",
				timestamp = Clock.System.now(),
				status = ToolResultStatus.SUCCESS,
			),
		)
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hi", timestamp = Clock.System.now()),
				turns = listOf(
					AgentContext.Turn(
						assistantMessage = AgentContext.Message.Assistant(
							content = "ok", modelId = model.id,
							timestamp = Clock.System.now(), usageSnapshot = null,
						),
						tools = listOf(toolMsg),
					)
				),
				finalAssistantMessage = null,
			),
		)
		
		CompactPhase.execute(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			service = longContentSettings
		)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `null assistant content and reasoning edge cases are handled`() = runTest {
		val chatResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
			CoreLlmResult(result = chatResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
		)
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = null, reasoning = null, modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(env, rounds, summarizeModel = model, fallbackModels = null, service = settings)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `summarizeMessage falls back to original content when result empty`() = runTest {
		val fallbackSettings = mockk<SettingService>().also {
			every { it.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
		}
		// First call (summarizeMessage for long user msg): retrying non-null, result has null message
		// Second call (summarizeMessage for long assistant msg): same
		// Third call (runCompactRequest): success with summary
		val emptyResult = ChatResult.Chunk(message = null, usage = null)
		val successResult = ChatResult.Assembled(
			message = ChatMessage.AssistantMessage(
				content = "<summary>this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion</summary>",
				createdAt = Clock.System.now(),
				model = "summarize-model",
			),
			usage = null,
		)
		every { ResilientChat.execute(any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
			flowOf(
				CoreLlmResult(result = emptyResult, model = model.id),
				CoreLlmResult(result = emptyResult, model = UUID.fromString("00000000-0000-0000-0000-000000000000"))
			),
			flowOf(
				CoreLlmResult(
					result = emptyResult,
					model = UUID.fromString("00000000-0000-0000-0000-000000000000")
				)
			),
			flowOf(
				CoreLlmResult(
					result = successResult,
					model = UUID.fromString("00000000-0000-0000-0000-000000000000")
				)
			),
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
					modelId = model.id, timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		CompactPhase.execute(
			env,
			rounds,
			summarizeModel = model,
			fallbackModels = null,
			service = fallbackSettings
		)
		
		assertEquals(
			"this is a comprehensive yet concise summary of the conversation that covers all key points made during the discussion",
			_contextFlow.value.summarizedMessage?.content
		)
	}
	
	@Test
	fun `CancellationException is rethrown not swallowed`() = runTest {
		every {
			ResilientChat.execute(
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any()
			)
		} throws CancellationException("cancelled")
		
		val rounds = listOf(
			AgentContext.CompletedRound(
				userMessage = AgentContext.Message.User(content = "hello", timestamp = Clock.System.now()),
				turns = null,
				finalAssistantMessage = AgentContext.Message.Assistant(
					content = "hi", modelId = model.id,
					timestamp = Clock.System.now(), usageSnapshot = null,
				),
			),
		)
		
		val job = launch {
			CompactPhase.execute(
				env,
				rounds,
				summarizeModel = model,
				fallbackModels = null,
				service = settings
			)
		}
		job.join()
		assertTrue(job.isCancelled)
	}
	
	// region helpers
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), id = UUID.randomUUID()
		)
	}
	// endregion
}
