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

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.api.types.Base64
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.llm.*
import io.github.autotweaker.api.types.llm.ModelData.*
import io.github.autotweaker.api.types.llm.ModelData.TokenPrice.PriceTier
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule
import io.github.autotweaker.api.types.llm.ProviderData.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.llm.LlmClientLoader
import io.mockk.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ResilientChatTest {
	
	private val mockService: SettingService = mockk<SettingService>().also { svc ->
		every { svc.get<SettingValue>(any()) } answers { firstArg<SettingDef<*>>().default }
	}
	private val testUrl = Url("https://api.test.com/v1")
	private val testPrice = Price(BigDecimal("0.01"), Currency.getInstance("USD"), 1_000_000)
	
	private val baseModelInfo = ModelInfo(
		modelId = "test-id",
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
	
	private fun provider(
		name: String = "test-provider",
		rules: List<ErrorHandlingRule> = emptyList(),
	) = Provider(UUID.randomUUID(), name, testUrl, "sk-test", rules)
	
	private fun model(
		provider: Provider = provider(),
		modelInfo: ModelInfo = baseModelInfo,
		config: Config? = null,
	) = Model(provider = provider, modelInfo = modelInfo, config = config, id = UUID.randomUUID())
	
	private fun assistantResult(content: String = "hello") = ChatResult.Assembled(
		message = ChatMessage.AssistantMessage(content, Clock.System.now(), null, null),
		finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
		usage = Usage(100, 50, 50),
	)
	
	private fun errorResult(statusCode: Int = 500) = ChatResult.Assembled(
		message = ChatMessage.ErrorMessage(
			"error", Clock.System.now(), statusCode
		),
	)
	
	private fun messages() = listOf(
		ChatMessage.UserMessage("hello", Clock.System.now())
	)
	
	@AfterEach
	fun cleanup() {
		unmockkObject(LlmClientLoader)
	}
	
	@Test
	fun `successful response from first model`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		coEvery { mockClient.chat(any(), any(), any()) } returns flow { emit(assistantResult("ok")) }
		
		val results = ResilientChat.execute(
			model = model(),
			fallbackModels = null,
			messages = messages(),
			service = mockService,
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
		val m1 = model(provider = providerWithRetry)
		val results = ResilientChat.execute(
			model = m1,
			fallbackModels = null,
			messages = messages(),
			service = mockService,
		).toList()
		
		assertEquals(3, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertEquals(m1.id, results[0].model)
		assertEquals("error", (results[1].result.message as ChatMessage.ErrorMessage).content)
		assertEquals(m1.id, results[1].model)
		val lastMsg = results[2].result.message as ChatMessage.AssistantMessage
		assertEquals("success after retry", lastMsg.content)
		assertEquals(m1.id, results[2].model)
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
		val m1 = model(p1)
		val m2 = model(p2)
		
		val results = ResilientChat.execute(
			model = m1,
			fallbackModels = listOf(m2),
			messages = messages(),
			service = mockService,
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertEquals(m1.id, results[0].model)
		assertEquals("fallback success", (results[1].result.message as ChatMessage.AssistantMessage).content)
		assertEquals(m2.id, results[1].model)
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
				any(), any(), any()
			)
		} returns flow { emit(assistantResult("provider fallback success")) }
		
		val providerSame = provider("same", listOf(ErrorHandlingRule(401, RecoveryStrategy.PROVIDER_FALLBACK)))
		val providerOther = provider("other")
		
		val results = ResilientChat.execute(
			model = model(providerSame),
			fallbackModels = listOf(model(providerSame), model(providerOther)),
			messages = messages(),
			service = mockService,
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[0].model)
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
				any(), any(), any()
			)
		} returns flow { emit(assistantResult("context fallback success")) }
		
		val smallerModelInfo = baseModelInfo.copy(contextWindow = 64000)
		val largerModelInfo = baseModelInfo.copy(contextWindow = 256000)
		
		val p1 = provider("p1", listOf(ErrorHandlingRule(400, RecoveryStrategy.CONTEXT_FALLBACK)))
		val p2 = provider("p2")
		val p3 = provider("p3")
		
		val results = ResilientChat.execute(
			model = model(p1, baseModelInfo),
			fallbackModels = listOf(
				model(p2, smallerModelInfo),
				model(p3, largerModelInfo),
			),
			messages = messages(),
			service = mockService,
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("error", (results[0].result.message as ChatMessage.ErrorMessage).content)
		assertNotNull(results[0].model)
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
			ResilientChat.execute(
				model = model(provider = p),
				fallbackModels = null,
				messages = messages(),
				service = mockService,
			).toList()
		}
		assertTrue(ex.message!!.contains("All LLM chat retries exhausted"))
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
		
		val img = Base64("AAAA")
		val imageMessages = listOf(
			ChatMessage.UserMessage("look", Clock.System.now(), listOf(img))
		)
		
		val results = ResilientChat.execute(
			model = model(provider("p1"), noImageModelInfo),
			fallbackModels = listOf(model(provider("p2"), imageModelInfo)),
			messages = imageMessages,
			service = mockService,
		).toList()
		
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
				ChatResult.Chunk(
					message = ChatMessage.AssistantMessage("", Clock.System.now(), "", null),
				)
			)
		}
		
		val results = ResilientChat.execute(
			model = model(),
			fallbackModels = null,
			messages = messages(),
			service = mockService,
		).toList()
		
		val msg = results[0].result.message as ChatMessage.AssistantMessage
		assertEquals(msg.content, null)
		assertEquals(msg.reasoningContent, null)
	}
	
	@Test
	fun `normalize empty strings in Assembled result`() = runTest {
		val mockClient = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("test-provider") } returns mockClient
		coEvery { mockClient.chat(any(), any(), any()) } returns flow {
			emit(
				ChatResult.Assembled(
					message = ChatMessage.AssistantMessage("", Clock.System.now(), "", null),
				)
			)
		}
		
		val results = ResilientChat.execute(
			model = model(),
			fallbackModels = null,
			messages = messages(),
			service = mockService,
		).toList()
		
		val msg = results[0].result.message as ChatMessage.AssistantMessage
		assertEquals(null, msg.content)
		assertEquals(null, msg.reasoningContent)
	}
	
	@Test
	fun `error with null status code triggers fallback`() = runTest {
		val mockClient1 = mockk<LlmClient>()
		val mockClient2 = mockk<LlmClient>()
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load("p1") } returns mockClient1
		every { LlmClientLoader.load("p2") } returns mockClient2
		
		coEvery { mockClient1.chat(any(), any(), any()) } returns flow {
			emit(ChatResult.Assembled(message = ChatMessage.ErrorMessage("no status", Clock.System.now(), null)))
		}
		coEvery {
			mockClient2.chat(
				any(), any(), any()
			)
		} returns flow { emit(assistantResult("fallback after null status")) }
		
		val p1 = provider("p1")
		val results = ResilientChat.execute(
			model = model(p1),
			fallbackModels = listOf(model(provider("p2"))),
			messages = messages(),
			service = mockService,
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("fallback after null status", (results[1].result.message as ChatMessage.AssistantMessage).content)
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
		
		val img = Base64("AAAA")
		val imageMessages = listOf(ChatMessage.UserMessage("look", Clock.System.now(), listOf(img)))
		
		ResilientChat.execute(
			model = model(provider("p1"), baseModelInfo.copy(supportsImage = false)),
			fallbackModels = null,
			messages = imageMessages,
			service = mockService,
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
		val mixedMessages = listOf(
			ChatMessage.UserMessage("hello", now),
			ChatMessage.AssistantMessage("response", now, "thinking...", null),
		)
		
		ResilientChat.execute(
			model = model(provider("p1"), baseModelInfo.copy(supportsReasoning = false)),
			fallbackModels = null,
			messages = mixedMessages,
			service = mockService,
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
		val mixedMessages = listOf(
			ChatMessage.UserMessage("hello", now),
			ChatMessage.AssistantMessage("a1", now, null, null),
			ChatMessage.AssistantMessage("a2", now, "think", null),
		)
		
		ResilientChat.execute(
			model = model(provider("p1"), baseModelInfo.copy(supportsReasoning = true)),
			fallbackModels = null,
			messages = mixedMessages,
			thinking = true,
			service = mockService,
		).toList()
		
		val msg1 = capturedRequest!!.messages[1] as ChatMessage.AssistantMessage
		val msg2 = capturedRequest!!.messages[2] as ChatMessage.AssistantMessage
		assertEquals("</think>", msg1.reasoningContent)
		assertEquals("think", msg2.reasoningContent)
	}
}
