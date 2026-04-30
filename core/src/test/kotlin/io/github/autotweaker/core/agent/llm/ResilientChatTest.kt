package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.Provider.ErrorHandlingRule
import io.github.autotweaker.core.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.Provider.Model.*
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.llm.*
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock

@Suppress("UnusedFlow")
class ResilientChatTest {
	
	private val mockClient = mockk<LlmClient>()
	
	@BeforeTest
	fun setUp() {
		mockkObject(LlmClientLoader)
		every { LlmClientLoader.load(any()) } returns mockClient
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(LlmClientLoader)
	}
	
	// region helpers
	
	private fun model(
		name: String,
		providerName: String = "test",
		contextWindow: Int = 4096,
		supportsImage: Boolean = false,
		supportsReasoning: Boolean = false,
		rules: List<ErrorHandlingRule> = emptyList(),
		temperature: Double? = null,
		maxTokens: Int? = null,
	) = Model(
		name = name,
		provider = Provider(
			name = providerName,
			baseUrl = Url("https://api.example.com"),
			apiKey = "key-$providerName",
			errorHandlingRules = rules,
		),
		modelInfo = ModelInfo(
			id = name,
			contextWindow = contextWindow,
			maxOutputTokens = 4096,
			price = TokenPrice(emptyList(), emptyList()),
			supportsStreaming = true,
			supportsToolCalls = false,
			supportsReasoning = supportsReasoning,
			supportsImage = supportsImage,
			supportsJsonOutput = true,
		),
		config = Config(
			temperature = temperature,
			maxTokens = maxTokens,
			compactContextUsage = null,
			compactTotalTokens = null,
		),
	)
	
	private fun successResult(content: String = "ok") = ChatResult(
		message = ChatMessage.AssistantMessage(
			content = content,
			createdAt = Clock.System.now(),
			model = "test",
		),
		finishReason = ChatResult.FinishReason("stop", ChatResult.FinishReason.Type.STOP),
	)
	
	private fun errorResult(statusCode: Int) = ChatResult(
		message = ChatMessage.ErrorMessage(
			content = "error $statusCode",
			createdAt = Clock.System.now(),
			statusCode = io.ktor.http.HttpStatusCode.fromValue(statusCode),
		),
	)
	
	private fun errorResultWithoutStatusCode() = ChatResult(
		message = ChatMessage.ErrorMessage(
			content = "network error",
			createdAt = Clock.System.now(),
			statusCode = null,
		),
	)
	
	private fun request(
		messages: List<ChatMessage> = listOf(
			ChatMessage.UserMessage("hi", Clock.System.now())
		)
	) = ChatRequest(model = "dummy", messages = messages)
	
	/** 设置 mockClient.chat 按顺序返回指定的 Flow 列表 */
	private fun mockChatSequence(vararg flows: Flow<ChatResult>) {
		var callIndex = 0
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			val flow = flows.getOrElse(callIndex) { flows.last() }
			callIndex++
			flow
		}
	}
	
	// endregion
	
	@Test
	fun `direct success no retry`() = runTest {
		mockChatSequence(flowOf(successResult("hello")))
		
		val results = resilientChat(
			model = model("m1"),
			fallbackModels = emptyList(),
			request = request(),
		).toList()
		
		assertEquals(1, results.size)
		assertEquals("hello", results[0].result.message?.content)
		coVerify(exactly = 1) { mockClient.chat(any(), any(), any()) }
	}
	
	@Test
	fun `RETRY strategy then success`() = runTest {
		val rules = listOf(ErrorHandlingRule(429, RecoveryStrategy.RETRY))
		mockChatSequence(
			flowOf(errorResult(429)),
			flowOf(successResult("retried")),
		)
		
		val results = resilientChat(
			model = model("m1", rules = rules),
			fallbackModels = emptyList(),
			request = request(),
			maxRetries = 3,
		).toList()
		
		assertEquals(2, results.size)
		assertTrue(results[0].result.message is ChatMessage.ErrorMessage)
		assertEquals("m1", results[0].retrying?.name)
		assertEquals("retried", results[1].result.message?.content)
		coVerify(exactly = 2) { mockClient.chat(any(), any(), any()) }
	}
	
	@Test
	fun `RETRY exhausted switch to fallback model`() = runTest {
		val rules = listOf(ErrorHandlingRule(500, RecoveryStrategy.RETRY))
		mockChatSequence(
			flowOf(errorResult(500)),
			flowOf(errorResult(500)),
			flowOf(successResult("fallback-ok")),
		)
		
		val results = resilientChat(
			model = model("m1", rules = rules),
			fallbackModels = listOf(model("m2")),
			request = request(),
			maxRetries = 2,
		).toList()
		
		assertEquals(3, results.size)
		assertEquals("m1", results[0].retrying?.name)   // RETRY 重试当前模型
		assertEquals("m2", results[1].retrying?.name)   // 耗尽后切换到 m2
		assertEquals("fallback-ok", results[2].result.message?.content)
	}
	
	@Test
	fun `FALLBACK strategy blocks current model`() = runTest {
		val rules = listOf(ErrorHandlingRule(503, RecoveryStrategy.FALLBACK))
		mockChatSequence(
			flowOf(errorResult(503)),
			flowOf(successResult("fallback-model-ok")),
		)
		
		val results = resilientChat(
			model = model("m1", rules = rules),
			fallbackModels = listOf(model("m2")),
			request = request(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("m2", results[0].retrying?.name)
		assertEquals("fallback-model-ok", results[1].result.message?.content)
	}
	
	@Test
	fun `CONTEXT_FALLBACK blocks smaller context models`() = runTest {
		val rules = listOf(ErrorHandlingRule(413, RecoveryStrategy.CONTEXT_FALLBACK))
		// m1: 4096, m2: 2048, m3: 8192
		// CONTEXT_FALLBACK 会过滤掉 contextWindow <= 4096 的，剩下 m3
		mockChatSequence(
			flowOf(errorResult(413)),
			flowOf(successResult("big-context-ok")),
		)
		
		val results = resilientChat(
			model = model("m1", contextWindow = 4096, rules = rules),
			fallbackModels = listOf(
				model("m2", contextWindow = 2048),
				model("m3", contextWindow = 8192),
			),
			request = request(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("m3", results[0].retrying?.name)
		assertEquals("big-context-ok", results[1].result.message?.content)
		// m2 被屏蔽了，直接用 m3
		coVerify(exactly = 2) { mockClient.chat(any(), any(), any()) }
	}
	
	@Test
	fun `PROVIDER_FALLBACK blocks same provider models`() = runTest {
		val rules = listOf(ErrorHandlingRule(500, RecoveryStrategy.PROVIDER_FALLBACK))
		// m1: providerA, m2: providerA, m3: providerB
		mockChatSequence(
			flowOf(errorResult(500)),
			flowOf(successResult("other-provider-ok")),
		)
		
		val results = resilientChat(
			model = model("m1", providerName = "providerA", rules = rules),
			fallbackModels = listOf(
				model("m2", providerName = "providerA"),
				model("m3", providerName = "providerB"),
			),
			request = request(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("m3", results[0].retrying?.name)
		assertEquals("other-provider-ok", results[1].result.message?.content)
	}
	
	@Test
	fun `no matching rule treated as FALLBACK`() = runTest {
		mockChatSequence(
			flowOf(errorResult(999)),
			flowOf(successResult("no-rule-fallback")),
		)
		
		val results = resilientChat(
			model = model("m1"), // 无规则
			fallbackModels = listOf(model("m2")),
			request = request(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("m2", results[0].retrying?.name)
		assertEquals("no-rule-fallback", results[1].result.message?.content)
	}
	
	@Test
	fun `all candidates exhausted throws exception`() = runTest {
		mockChatSequence(flowOf(errorResult(500)))
		
		assertFailsWith<IllegalStateException> {
			resilientChat(
				model = model("m1"),
				fallbackModels = listOf(model("m2")),
				request = request(),
				maxRetries = 1,
			).toList()
		}
	}
	
	@Test
	fun `image with capable model blocks incapable ones`() = runTest {
		val pic = Base64("dGVzdA==")
		val req = request(
			messages = listOf(
				ChatMessage.UserMessage("hi", Clock.System.now(), pictures = listOf(pic))
			)
		)
		// m1: 不支持图像, m2: 支持图像
		mockChatSequence(flowOf(successResult("image-ok")))
		
		val results = resilientChat(
			model = model("m1", supportsImage = false),
			fallbackModels = listOf(model("m2", supportsImage = true)),
			request = req,
		).toList()
		
		assertEquals(1, results.size)
		assertEquals("image-ok", results[0].result.message?.content)
		// 应该只调用了 m2（图像模型），m1 被屏蔽
		coVerify(exactly = 1) { mockClient.chat(any(), any(), any()) }
	}
	
	@Test
	fun `image no capable model strips pictures`() = runTest {
		val pic = Base64("dGVzdA==")
		val req = request(
			messages = listOf(
				ChatMessage.UserMessage("hi", Clock.System.now(), pictures = listOf(pic))
			)
		)
		
		var capturedRequest: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			capturedRequest = arg(0)
			flowOf(successResult("stripped"))
		}
		
		val results = resilientChat(
			model = model("m1", supportsImage = false),
			fallbackModels = listOf(model("m2", supportsImage = false)),
			request = req,
		).toList()
		
		assertEquals(1, results.size)
		assertEquals("stripped", results[0].result.message?.content)
		assertNotNull(capturedRequest)
		val userMsg = capturedRequest!!.messages.first() as ChatMessage.UserMessage
		assertEquals(null, userMsg.pictures)
	}
	
	// region 思维链过滤测试
	
	private fun assistantMsg(
		content: String = "reply",
		reasoningContent: String? = null,
	) = ChatMessage.AssistantMessage(
		content = content,
		createdAt = Clock.System.now(),
		reasoningContent = reasoningContent,
		model = "test",
	)
	
	private fun userMsg(text: String) = ChatMessage.UserMessage(text, Clock.System.now())
	
	private suspend fun capturedMessages(
		model: Model,
		messages: List<ChatMessage>,
		thinking: Boolean? = null,
	): List<ChatMessage> = capturedRequest(model, messages, thinking).messages
	
	private suspend fun capturedRequest(
		model: Model,
		messages: List<ChatMessage>,
		thinking: Boolean? = null,
	): ChatRequest {
		var captured: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			captured = arg(0)
			flowOf(successResult())
		}
		resilientChat(
			model = model,
			fallbackModels = emptyList(),
			request = ChatRequest(model = "dummy", messages = messages, thinking = thinking),
		).toList()
		return captured!!
	}
	
	@Test
	fun `non-reasoning model strips all reasoning content`() = runTest {
		val msgs = capturedMessages(
			model = model("m1", supportsReasoning = false),
			messages = listOf(
				userMsg("first"),
				assistantMsg("r1", reasoningContent = "think1"),
				userMsg("second"),
				assistantMsg("r2", reasoningContent = "think2"),
			),
		)
		
		assertEquals(null, (msgs[1] as ChatMessage.AssistantMessage).reasoningContent)
		assertEquals(null, (msgs[3] as ChatMessage.AssistantMessage).reasoningContent)
	}
	
	@Test
	fun `thinking mode preserves all reasoning content`() = runTest {
		val msgs = capturedMessages(
			model = model("m1", supportsReasoning = true),
			thinking = true,
			messages = listOf(
				userMsg("first"),
				assistantMsg("r1", reasoningContent = "think1"),
				userMsg("second"),
				assistantMsg("r2", reasoningContent = "think2"),
			),
		)
		
		assertEquals("think1", (msgs[1] as ChatMessage.AssistantMessage).reasoningContent)
		assertEquals("think2", (msgs[3] as ChatMessage.AssistantMessage).reasoningContent)
	}
	
	@Test
	fun `reasoning capable but disabled strips all`() = runTest {
		val msgs = capturedMessages(
			model = model("m1", supportsReasoning = true),
			thinking = false,
			messages = listOf(
				userMsg("first"),
				assistantMsg("r1", reasoningContent = "think1"),
				userMsg("second"),
				assistantMsg("r2", reasoningContent = "think2"),
			),
		)
		
		assertEquals(null, (msgs[1] as ChatMessage.AssistantMessage).reasoningContent)
		assertEquals(null, (msgs[3] as ChatMessage.AssistantMessage).reasoningContent)
	}
	
	@Test
	fun `messages without reasoning unaffected`() = runTest {
		val msgs = capturedMessages(
			model = model("m1", supportsReasoning = false),
			messages = listOf(
				userMsg("first"),
				assistantMsg("r1"),
			),
		)
		
		assertEquals("r1", msgs[1].content)
	}
	
	@Test
	fun `non-reasoning model strips thinking field`() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = true,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(null, req.thinking)
	}
	
	@Test
	fun `reasoning capable model preserves thinking field`() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = true),
			thinking = true,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(true, req.thinking)
	}
	
	@Test
	fun `thinking false does not strip`() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = false,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(false, req.thinking)
	}
	
	@Test
	fun `thinking null does not strip`() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = null,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(null, req.thinking)
	}
	
	// endregion
	
	// region 分支覆盖测试
	
	@Test
	fun `error without status code treated as FALLBACK`() = runTest {
		mockChatSequence(
			flowOf(errorResultWithoutStatusCode()),
			flowOf(successResult("no-status-fallback")),
		)
		
		val results = resilientChat(
			model = model("m1"),
			fallbackModels = listOf(model("m2")),
			request = request(),
		).toList()
		
		assertEquals(2, results.size)
		assertEquals("m2", results[0].retrying?.name)
		assertEquals("no-status-fallback", results[1].result.message?.content)
	}
	
	@Test
	fun `config non-null passes temperature and maxTokens`() = runTest {
		var captured: ChatRequest? = null
		coEvery { mockClient.chat(any(), any(), any()) } answers {
			captured = arg(0)
			flowOf(successResult())
		}
		
		resilientChat(
			model = model("m1", temperature = 0.7, maxTokens = 1000),
			fallbackModels = emptyList(),
			request = request(),
		).toList()
		
		assertNotNull(captured)
		assertEquals(0.7, captured!!.temperature)
		assertEquals(1000, captured!!.maxTokens)
	}
	
	@Test
	fun `fallbackModels null works normally`() = runTest {
		mockChatSequence(flowOf(successResult("ok")))
		
		val results = resilientChat(
			model = model("m1"),
			fallbackModels = null,
			request = request(),
		).toList()
		
		assertEquals(1, results.size)
		assertEquals("ok", results[0].result.message?.content)
	}
	
	@Test
	fun `maxRetries 0 throws IllegalArgumentException`() = runTest {
		assertFailsWith<IllegalArgumentException> {
			resilientChat(
				model = model("m1"),
				fallbackModels = emptyList(),
				request = request(),
				maxRetries = 0,
			).toList()
		}
	}
	
	// endregion
}
