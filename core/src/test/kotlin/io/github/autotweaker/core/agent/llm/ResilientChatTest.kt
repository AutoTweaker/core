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
import io.github.autotweaker.core.Provider.ErrorHandlingRule
import io.github.autotweaker.core.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.Provider.Model.*
import io.github.autotweaker.core.Provider.Model.TokenPrice.PriceTier
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.llm.*
import io.mockk.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.math.BigDecimal
import java.util.*
import kotlin.test.*
import kotlin.time.Clock

class ResilientChatTest {
	
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	
	private val baseModelInfo = ModelInfo(
		id = "test-id",
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
	
	private fun provider(
		name: String = "test-provider",
		rules: List<ErrorHandlingRule> = emptyList(),
	) = Provider(name, testUrl, "sk-test", rules)
	
	private fun model(
		name: String = "test-model",
		provider: Provider = provider(),
		modelInfo: ModelInfo = baseModelInfo,
		config: Config? = null,
	) = Model(name, provider, modelInfo, config)
	
	private fun assistantResult(content: String = "hello") = ChatResult(
		message = ChatMessage.AssistantMessage(content, Clock.System.now(), null, null),
		finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
		usage = Usage(100, 50, 50),
	)
	
	private fun errorResult(statusCode: Int = 500) = ChatResult(
		message = ChatMessage.ErrorMessage(
			"error",
			Clock.System.now(),
			io.ktor.http.HttpStatusCode.fromValue(statusCode)
		),
	)
	
	private fun chatRequest(modelName: String = "test-model") = ChatRequest(
		model = modelName,
		messages = listOf(
			ChatMessage.UserMessage("hello", Clock.System.now())
		),
	)
	
	@After
	fun cleanup() {
		unmockkObject(LlmClientLoader)
	}
	
	@Test
	fun `successful response from first model`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		coEvery { mockClient.chat(any(), any(), any()) } returns flow { emit(assistantResult("ok")) }
		
		val results = resilientChat(
			model = model(),
			fallbackModels = null,
			request = chatRequest(),
		).toList()
		
		assertEquals(1, results.size)
		assertEquals("ok", (results[0].result.message as ChatMessage.AssistantMessage).content)
		assertNotNull(results[0].result.finishReason)
	}
	
	@Test
	fun `retry strategy retries up to maxRetries`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		
		// First two calls fail with 429 (RETRY), third succeeds
		val responses = mutableListOf(
			flow { emit(errorResult(429)) },
			flow { emit(errorResult(429)) },
			flow { emit(assistantResult("success after retry")) },
		)
		var callIndex = 0
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			responses[callIndex++]
		}
		
		val providerWithRetry = provider(
			rules = listOf(ErrorHandlingRule(429, RecoveryStrategy.RETRY))
		)
		val results = resilientChat(
			model = model(provider = providerWithRetry),
			fallbackModels = null,
			request = chatRequest(),
			maxRetries = 3,
		).toList()
		
		// First call: error + retrying signal
		// Second call: error + retrying signal
		// Third call: success
		assertEquals(3, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[0].retrying)
		assertEquals("error", (results[1].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[1].retrying)
		val lastMsg = results[2].result.message as ChatMessage.AssistantMessage
		assertEquals("success after retry", lastMsg.content)
	}
	
	@Test
	fun `fallback strategy drops current model and tries next`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("provider1") } returns mockClient1
		every { LlmClientLoader.load("provider2") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow { emit(errorResult(503)) }
		coEvery { mockClient2.chat(any(), any(), any()) } returns flow { emit(assistantResult("fallback success")) }
		
		val p1 = provider("provider1", listOf(ErrorHandlingRule(503, RecoveryStrategy.FALLBACK)))
		val p2 = provider("provider2")
		val m1 = model("model1", p1)
		val m2 = model("model2", p2)
		
		val results = resilientChat(
			model = m1,
			fallbackModels = listOf(m2),
			request = chatRequest(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertEquals(m2, results[0].retrying)
		assertEquals("fallback success", (results[1].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `provider fallback filters by provider name`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("same") } returns mockClient1
		every { LlmClientLoader.load("other") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow { emit(errorResult(401)) }
		coEvery {
			mockClient2.chat(
				any(),
				any(),
				any()
			)
		} returns flow { emit(assistantResult("provider fallback success")) }
		
		// m1 fails with PROVIDER_FALLBACK on provider "same"
		// m2 has same provider "same" → filtered out
		// m3 has different provider "other" → used
		val providerSame = provider("same", listOf(ErrorHandlingRule(401, RecoveryStrategy.PROVIDER_FALLBACK)))
		val providerOther = provider("other")
		
		val results = resilientChat(
			model = model("m1", providerSame),
			fallbackModels = listOf(model("m2", providerSame), model("m3", providerOther)),
			request = chatRequest(),
		).toList()
		
		// m1 fails → filters m2 (same provider) → m3 succeeds
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[0].retrying)
		assertEquals("provider fallback success", (results[1].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `context fallback filters by context window`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		val mockClient3 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient1
		every { LlmClientLoader.load("p2") } returns mockClient2
		every { LlmClientLoader.load("p3") } returns mockClient3
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow { emit(errorResult(400)) }
		coEvery { mockClient2.chat(any(), any(), any()) } returns flow { emit(assistantResult("should not be used")) }
		coEvery {
			mockClient3.chat(
				any(),
				any(),
				any()
			)
		} returns flow { emit(assistantResult("context fallback success")) }
		
		// m1 has contextWindow=128000, fails with CONTEXT_FALLBACK
		// m2 has contextWindow=64000 (smaller) → should be filtered out
		// m3 has contextWindow=256000 (larger) → should be used
		val smallerModelInfo = baseModelInfo.copy(contextWindow = 64000)
		val largerModelInfo = baseModelInfo.copy(contextWindow = 256000)
		
		val p1 = provider("p1", listOf(ErrorHandlingRule(400, RecoveryStrategy.CONTEXT_FALLBACK)))
		val p2 = provider("p2")
		val p3 = provider("p3")
		
		val results = resilientChat(
			model = model("m1", p1, baseModelInfo),
			fallbackModels = listOf(
				model("m2", p2, smallerModelInfo),
				model("m3", p3, largerModelInfo),
			),
			request = chatRequest(),
		).toList()
		
		// m2 is filtered (smaller context), m3 is used
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[0].retrying)
		assertEquals("context fallback success", (results[1].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `all models exhausted throws IllegalStateException`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		coEvery { mockClient.chat(any(), any(), any()) } returns flow { emit(errorResult(500)) }
		
		val p = provider(rules = listOf(ErrorHandlingRule(500, RecoveryStrategy.FALLBACK)))
		val ex = assertFailsWith<IllegalStateException> {
			resilientChat(
				model = model(provider = p),
				fallbackModels = null,
				request = chatRequest(),
			).toList()
		}
		assertTrue(ex.message!!.contains("All candidate models exhausted"))
	}
	
	@Test
	fun `maxRetries must be positive`() = runTest {
		val ex = assertFailsWith<IllegalArgumentException> {
			resilientChat(
				model = model(),
				fallbackModels = null,
				request = chatRequest(),
				maxRetries = 0,
			).toList()
		}
		assertTrue(ex.message!!.contains("maxRetries must be positive"))
	}
	
	@Test
	fun `image support filtering excludes non image models`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient1
		every { LlmClientLoader.load("p2") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow { emit(assistantResult("should not be called")) }
		coEvery { mockClient2.chat(any(), any(), any()) } returns flow { emit(assistantResult("image model used")) }
		
		val imageModelInfo = baseModelInfo.copy(supportsImage = true)
		val noImageModelInfo = baseModelInfo.copy(supportsImage = false)
		
		val img = io.github.autotweaker.core.Base64("AAAA")
		val requestWithImage = ChatRequest(
			model = "test-model",
			messages = listOf(
				ChatMessage.UserMessage("look", Clock.System.now(), listOf(img))
			),
		)
		
		val results = resilientChat(
			model = model("m1", provider("p1"), noImageModelInfo),
			fallbackModels = listOf(model("m2", provider("p2"), imageModelInfo)),
			request = requestWithImage,
		).toList()
		
		// m1 should be filtered out (no image support), m2 should be used
		assertEquals(1, results.size)
		assertEquals("image model used", (results[0].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `normalize empty strings to null`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		coEvery { mockClient.chat(any(), any(), any()) } returns flow {
			emit(
				ChatResult(
					message = ChatMessage.AssistantMessage("", Clock.System.now(), "", null),
				)
			)
		}
		
		val results = resilientChat(
			model = model(),
			fallbackModels = null,
			request = chatRequest(),
		).toList()
		
		val msg = results[0].result.message as ChatMessage.AssistantMessage
		assertEquals(msg.content, null)
		assertEquals(msg.reasoningContent, null)
	}
	
	@Test
	fun `error with null status code triggers fallback`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient1
		every { LlmClientLoader.load("p2") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow {
			emit(ChatResult(message = ChatMessage.ErrorMessage("no status", Clock.System.now(), null)))
		}
		coEvery {
			mockClient2.chat(
				any(),
				any(),
				any()
			)
		} returns flow { emit(assistantResult("fallback after null status")) }
		
		val p1 = provider("p1")
		val results = resilientChat(
			model = model("m1", p1),
			fallbackModels = listOf(model("m2", provider("p2"))),
			request = chatRequest(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("fallback after null status", (results[1].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `retry strategy exhausts and drops model`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient1
		every { LlmClientLoader.load("p2") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow { emit(errorResult(429)) }
		coEvery {
			mockClient2.chat(
				any(),
				any(),
				any()
			)
		} returns flow { emit(assistantResult("success after retry exhaust")) }
		
		val p1 = provider("p1", listOf(ErrorHandlingRule(429, RecoveryStrategy.RETRY)))
		val results = resilientChat(
			model = model("m1", p1),
			fallbackModels = listOf(model("m2", provider("p2"))),
			request = chatRequest(),
			maxRetries = 2,
		).toList()
		
		assertEquals(3, results.size)
		assertEquals("success after retry exhaust", (results[2].result.message as ChatMessage.AssistantMessage).content)
	}
	
	@Test
	fun `strip pictures when no image model available`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient
		
		var capturedRequest: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			capturedRequest = firstArg()
			flow { emit(assistantResult("stripped")) }
		}
		
		val img = io.github.autotweaker.core.Base64("AAAA")
		val requestWithImage = ChatRequest(
			model = "test",
			messages = listOf(ChatMessage.UserMessage("look", Clock.System.now(), listOf(img)))
		)
		
		resilientChat(
			model = model("m1", provider("p1"), baseModelInfo.copy(supportsImage = false)),
			fallbackModels = null,
			request = requestWithImage,
		).toList()
		
		val userMsg = capturedRequest!!.messages[0] as ChatMessage.UserMessage
		assertEquals(null, userMsg.pictures)
	}
	
	@Test
	fun `strip reasoning from assistant message in adapt`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient
		
		var capturedRequest: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			capturedRequest = firstArg()
			flow { emit(assistantResult("ok")) }
		}
		
		val now = Clock.System.now()
		val requestWithAssistant = ChatRequest(
			model = "test",
			messages = listOf(
				ChatMessage.UserMessage("hello", now),
				ChatMessage.AssistantMessage("response", now, "thinking...", null),
			),
		)
		
		resilientChat(
			model = model("m1", provider("p1"), baseModelInfo.copy(supportsReasoning = false)),
			fallbackModels = null,
			request = requestWithAssistant,
		).toList()
		
		val asstMsg = capturedRequest!!.messages[1] as ChatMessage.AssistantMessage
		assertEquals(null, asstMsg.reasoningContent)
	}
	
	@Test
	fun `adapt assistant reasoning content when thinking enabled`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient
		
		var capturedRequest: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			capturedRequest = firstArg()
			flow { emit(assistantResult("ok")) }
		}
		
		val now = Clock.System.now()
		val request = ChatRequest(
			model = "test",
			messages = listOf(
				ChatMessage.UserMessage("hello", now),
				ChatMessage.AssistantMessage("a1", now, null, null),
				ChatMessage.AssistantMessage("a2", now, "think", null),
			),
			thinking = true,
		)
		
		resilientChat(
			model = model("m1", provider("p1"), baseModelInfo.copy(supportsReasoning = true)),
			fallbackModels = null,
			request = request,
		).toList()
		
		val msg1 = capturedRequest!!.messages[1] as ChatMessage.AssistantMessage
		val msg2 = capturedRequest!!.messages[2] as ChatMessage.AssistantMessage
		assertEquals("", msg1.reasoningContent)
		assertEquals("think", msg2.reasoningContent)
	}
}
