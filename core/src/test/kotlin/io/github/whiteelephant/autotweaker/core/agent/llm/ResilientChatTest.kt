package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.Base64
import io.github.whiteelephant.autotweaker.core.Price
import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule
import io.github.whiteelephant.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule.RecoveryStrategy
import io.github.whiteelephant.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.TokenPrice
import io.github.whiteelephant.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.TokenPrice.PriceTier
import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Currency
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResilientChatTest {

    @BeforeTest
    fun setUp() {
        mockkStatic("io.github.whiteelephant.autotweaker.core.agent.llm.ForwardChatKt")
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("io.github.whiteelephant.autotweaker.core.agent.llm.ForwardChatKt")
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
        contextWindow = contextWindow,
        maxOutputTokens = 4096,
        price = TokenPrice(emptyList(), emptyList()),
        supportsStreaming = true,
        supportsToolCalls = false,
        supportsReasoning = supportsReasoning,
        supportsImage = supportsImage,
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

    /** 设置 forwardChat mock 按顺序返回指定的 Flow 列表 */
    private fun mockForwardSequence(vararg flows: Flow<ChatResult>) {
        var callIndex = 0
        coEvery { forwardChat(any(), any(), any(), any()) } answers {
            val flow = flows.getOrElse(callIndex) { flows.last() }
            callIndex++
            flow
        }
    }

    // endregion

    @Test
    fun `直接成功不重试`() = runTest {
        mockForwardSequence(flowOf(successResult("hello")))

        val results = resilientChat(
            model = model("m1"),
            fallbackModels = emptyList(),
            request = request(),
        ).toList()

        assertEquals(1, results.size)
        assertEquals("hello", results[0].result.message?.content)
        coVerify(exactly = 1) { forwardChat(any(), any(), any(), any()) }
    }

    @Test
    fun `RETRY 策略重试后成功`() = runTest {
        val rules = listOf(ErrorHandlingRule(429, RecoveryStrategy.RETRY))
        mockForwardSequence(
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
        coVerify(exactly = 2) { forwardChat(any(), any(), any(), any()) }
    }

    @Test
    fun `RETRY 耗尽后切换到 fallback 模型`() = runTest {
        val rules = listOf(ErrorHandlingRule(500, RecoveryStrategy.RETRY))
        mockForwardSequence(
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
        mockForwardSequence(
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
        mockForwardSequence(
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
        coVerify(exactly = 2) { forwardChat(any(), any(), any(), any()) }
    }

    @Test
    fun `PROVIDER_FALLBACK 屏蔽同 provider 的模型`() = runTest {
        val rules = listOf(ErrorHandlingRule(500, RecoveryStrategy.PROVIDER_FALLBACK))
        // m1: providerA, m2: providerA, m3: providerB
        mockForwardSequence(
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
        mockForwardSequence(
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
    fun `所有候选耗尽抛异常`() = runTest {
        mockForwardSequence(flowOf(errorResult(500)))

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
    fun `图像存在支持模型时屏蔽不支持的`() = runTest {
        val pic = Base64("dGVzdA==")
        val req = request(
            messages = listOf(
                ChatMessage.UserMessage("hi", Clock.System.now(), pictures = listOf(pic))
            )
        )
        // m1: 不支持图像, m2: 支持图像
        mockForwardSequence(flowOf(successResult("image-ok")))

        val results = resilientChat(
            model = model("m1", supportsImage = false),
            fallbackModels = listOf(model("m2", supportsImage = true)),
            request = req,
        ).toList()

        assertEquals(1, results.size)
        assertEquals("image-ok", results[0].result.message?.content)
        // 应该只调用了 m2（图像模型），m1 被屏蔽
        coVerify(exactly = 1) { forwardChat(any(), any(), any(), any()) }
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
        coEvery { forwardChat(any(), any(), any(), any()) } answers {
            capturedRequest = arg(3)
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
    ): List<ChatMessage> {
        var captured: ChatRequest? = null
        coEvery { forwardChat(any(), any(), any(), any()) } answers {
            captured = arg(3)
            flowOf(successResult())
        }
        resilientChat(
            model = model,
            fallbackModels = emptyList(),
            request = ChatRequest(model = "dummy", messages = messages, thinking = thinking),
        ).toList()
        return captured!!.messages
    }

    @Test
    fun `不支持思考的模型完全剔除思维链`() = runTest {
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
    fun `支持思考的模型仅保留最新用户消息后的思维链`() = runTest {
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
    fun `支持思考但未启用时剔除全部思维链`() = runTest {
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
    fun `无思维链的消息不受影响`() = runTest {
        val msgs = capturedMessages(
            model = model("m1", supportsReasoning = false),
            messages = listOf(
                userMsg("first"),
                assistantMsg("r1"),
            ),
        )

        assertEquals("r1", msgs[1].content)
    }

    // endregion
}
