package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.Base64
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.*
import io.github.autotweaker.core.llm.*
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock

@Suppress("UnusedFlow", "TestFunctionName")
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
			temperature = null,
			maxTokens = null,
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
	fun 直接成功不重试() = runTest {
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
	fun `RETRY 策略重试后成功`() = runTest {
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
	fun `RETRY 耗尽后切换到 fallback 模型`() = runTest {
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
	fun `FALLBACK 策略屏蔽当前模型`() = runTest {
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
	fun `CONTEXT_FALLBACK 屏蔽上下文小的模型`() = runTest {
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
	fun `PROVIDER_FALLBACK 屏蔽同 provider 的模型`() = runTest {
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
	fun `无匹配规则视为 FALLBACK`() = runTest {
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
	fun 所有候选耗尽抛异常() = runTest {
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
	fun 图像存在支持模型时屏蔽不支持的() = runTest {
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
	fun `图像无支持模型时剔除 pictures`() = runTest {
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
	fun 不支持思考的模型完全剔除思维链() = runTest {
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
	fun 支持思考的模型仅保留最新用户消息后的思维链() = runTest {
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
		
		assertEquals(null, (msgs[1] as ChatMessage.AssistantMessage).reasoningContent)
		assertEquals("think2", (msgs[3] as ChatMessage.AssistantMessage).reasoningContent)
	}
	
	@Test
	fun 支持思考但未启用时剔除全部思维链() = runTest {
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
	fun 无思维链的消息不受影响() = runTest {
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
	fun 不支持思考的模型剔除thinking字段() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = true,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(null, req.thinking)
	}
	
	@Test
	fun 支持思考的模型保留thinking字段() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = true),
			thinking = true,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(true, req.thinking)
	}
	
	@Test
	fun thinking为false时不剔除() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = false,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(false, req.thinking)
	}
	
	@Test
	fun thinking为null时不剔除() = runTest {
		val req = capturedRequest(
			model = model("m1", supportsReasoning = false),
			thinking = null,
			messages = listOf(userMsg("hello")),
		)
		
		assertEquals(null, req.thinking)
	}
	
	// endregion
}
