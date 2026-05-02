package io.github.autotweaker.core.llm.provider.deepseek

import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.base.openai.OpenAiRequest
import kotlin.test.*
import kotlin.time.Clock

class DeepSeekClientMappingTest {
    
    private val now = Clock.System.now()
    private val client = DeepSeekClient()
    
    private fun <T> invokeProtected(name: String, vararg args: Any?): T {
        val method = DeepSeekClient::class.java.declaredMethods
            .first { it.name == name && it.parameterTypes.size == args.size }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, *args) as T
    }
    
    private fun createRequestBody(request: ChatRequest): DeepSeekRequest =
        invokeProtected("createRequestBody", request)
    
    private fun mapToChatResult(response: DeepSeekResponse): ChatResult =
        invokeProtected("mapToChatResult", response)
    
    private fun mapChunkToChatResult(chunk: DeepSeekStreamChunk): ChatResult =
        invokeProtected("mapChunkToChatResult", chunk)
    
    private fun extractToolCalls(chunk: DeepSeekStreamChunk): List<*>? =
        invokeProtected("extractToolCalls", chunk)
    
    // region createRequestBody
    
    @Test
    fun `createRequestBody maps messages correctly`() {
        val userMsg = ChatMessage.UserMessage("hello", now)
        val request = ChatRequest(model = "deepseek-v4-pro", messages = listOf(userMsg))
        
        val body = createRequestBody(request)
        assertEquals("deepseek-v4-pro", body.model)
        assertEquals(1, body.messages.size)
        assertIs<DeepSeekMessage.UserMessage>(body.messages[0])
        assertEquals("hello", body.messages[0].content)
    }
    
    @Test
    fun `createRequestBody maps SystemMessage`() {
        val sysMsg = ChatMessage.SystemMessage("system prompt", now)
        val request = ChatRequest(model = "test", messages = listOf(sysMsg))
        val body = createRequestBody(request)
        assertIs<DeepSeekMessage.SystemMessage>(body.messages[0])
    }
    
    @Test
    fun `createRequestBody maps AssistantMessage with tool calls`() {
        val assistant = ChatMessage.AssistantMessage(
            content = "using tool",
            createdAt = now,
            toolCalls = listOf(
                ChatMessage.AssistantMessage.ToolCall("id1", "func1", "{}")
            )
        )
        val request = ChatRequest(model = "test", messages = listOf(assistant))
        val body = createRequestBody(request)
        
        val msg = body.messages[0] as DeepSeekMessage.AssistantMessage
        assertEquals("using tool", msg.content)
        assertEquals(1, msg.toolCalls?.size)
        assertEquals("id1", msg.toolCalls!![0].id)
        assertEquals("func1", msg.toolCalls[0].function.name)
    }
    
    @Test
    fun `createRequestBody maps ToolMessage`() {
        val tool = ChatMessage.ToolMessage("result", now, "call-1")
        val request = ChatRequest(model = "test", messages = listOf(tool))
        val body = createRequestBody(request)
        
        val msg = body.messages[0] as DeepSeekMessage.ToolMessage
        assertEquals("result", msg.content)
        assertEquals("call-1", msg.toolCallId)
    }
    
    @Test
    fun `createRequestBody filters ErrorMessage`() {
        val errorMsg = ChatMessage.ErrorMessage("error", now, null)
        val userMsg = ChatMessage.UserMessage("hi", now)
        val request = ChatRequest(model = "test", messages = listOf(errorMsg, userMsg))
        
        val body = createRequestBody(request)
        assertEquals(1, body.messages.size)
        assertIs<DeepSeekMessage.UserMessage>(body.messages[0])
    }
    
    @Test
    fun `createRequestBody includes tools and thinking`() {
        val userMsg = ChatMessage.UserMessage("hi", now)
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"key":"value"}""")
        val request = ChatRequest(
            model = "test",
            messages = listOf(userMsg),
            tools = listOf(ChatRequest.Tool("read_file", "read file", json)),
            thinking = true,
            temperature = 0.7,
            maxTokens = 1000,
            topP = 0.9,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            toolCallRequired = true,
            stream = true,
            responseFormat = ChatRequest.ResponseFormat(ChatRequest.ResponseFormat.Type.JSON_OBJECT)
        )
        
        val body = createRequestBody(request)
        assertEquals(1, body.tools?.size)
        assertEquals("read_file", body.tools!![0].function.name)
        assertEquals(OpenAiRequest.Thinking.Type.ENABLED, body.thinking?.type)
        assertEquals(0.7, body.temperature)
        assertEquals(1000, body.maxCompletionTokens)
        assertEquals(0.9, body.topP)
        assertEquals(0.5, body.frequencyPenalty)
        assertEquals(0.3, body.presencePenalty)
        assertNotNull(body.streamOptions)
        assertEquals(true, body.streamOptions.includeUsage)
        assertEquals(ToolChoice.REQUIRED, body.toolChoice)
    }
    
    @Test
    fun `createRequestBody toolCallRequired false sets NONE`() {
        val userMsg = ChatMessage.UserMessage("hi", now)
        val request = ChatRequest(model = "test", messages = listOf(userMsg), toolCallRequired = false)
        val body = createRequestBody(request)
        assertEquals(ToolChoice.NONE, body.toolChoice)
    }
    
    @Test
    fun `createRequestBody toolCallRequired null sets null`() {
        val userMsg = ChatMessage.UserMessage("hi", now)
        val request = ChatRequest(model = "test", messages = listOf(userMsg), toolCallRequired = null)
        val body = createRequestBody(request)
        assertNull(body.toolChoice)
    }
    
    // endregion
    
    // region mapToChatResult
    
    @Test
    fun `mapToChatResult maps response correctly`() {
        val response = DeepSeekResponse(
            id = "resp-1", created = now, model = "deepseek-v4-pro",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(
                        content = "hello world",
                        reasoningContent = "thinking...",
                        toolCalls = listOf(
                            DeepSeekMessage.AssistantMessage.ToolCall(
                                id = "t1",
                                function = DeepSeekMessage.AssistantMessage.ToolCall.Function("read", "{}")
                            )
                        )
                    ),
                    finishReason = DeepSeekFinishReason.STOP
                )
            ),
            usage = DeepSeekUsage(
                completionTokens = 50,
                promptTokens = 50,
                totalTokens = 100,
                promptCacheHitTokens = 10,
                promptCacheMissTokens = 40
            )
        )
        
        val result = mapToChatResult(response)
        assertIs<ChatMessage.AssistantMessage>(result.message)
        assertEquals("hello world", result.message.content)
        assertEquals("thinking...", result.message.reasoningContent)
        assertEquals(1, result.message.toolCalls?.size)
        assertEquals(ChatResult.FinishReason.Type.STOP, result.finishReason?.type)
        assertEquals(100, result.usage?.totalTokens)
        assertEquals(10, result.usage?.cacheHitTokens)
        assertEquals(40, result.usage?.cacheMissTokens)
    }
    
    @Test
    fun `mapToChatResult handles empty choices`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = emptyList(),
            usage = DeepSeekUsage(0, 0, 0)
        )
        val result = mapToChatResult(response)
        assertNull(result.message?.content)
    }
    
    @Test
    fun `mapToChatResult maps finish reason TOOL_CALLS`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(content = "calling"),
                    finishReason = DeepSeekFinishReason.TOOL_CALLS
                )
            ),
            usage = DeepSeekUsage(0, 0, 0)
        )
        assertEquals(ChatResult.FinishReason.Type.TOOL, mapToChatResult(response).finishReason?.type)
    }
    
    @Test
    fun `mapToChatResult maps finish reason FILTER`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(content = "blocked"),
                    finishReason = DeepSeekFinishReason.CONTENT_FILTER
                )
            ),
            usage = DeepSeekUsage(0, 0, 0)
        )
        assertEquals(ChatResult.FinishReason.Type.FILTER, mapToChatResult(response).finishReason?.type)
    }
    
    @Test
    fun `mapToChatResult maps finish reason LENGTH`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(content = "truncated"),
                    finishReason = DeepSeekFinishReason.LENGTH
                )
            ),
            usage = DeepSeekUsage(0, 0, 0)
        )
        assertEquals(ChatResult.FinishReason.Type.LENGTH, mapToChatResult(response).finishReason?.type)
    }
    
    @Test
    fun `mapToChatResult maps finish reason INSUFFICIENT_SYSTEM_RESOURCE to ERROR`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(content = "error"),
                    finishReason = DeepSeekFinishReason.INSUFFICIENT_SYSTEM_RESOURCE
                )
            ),
            usage = DeepSeekUsage(0, 0, 0)
        )
        assertEquals(ChatResult.FinishReason.Type.ERROR, mapToChatResult(response).finishReason?.type)
    }
    
    @Test
    fun `mapToChatResult includes reasoning tokens from details`() {
        val response = DeepSeekResponse(
            id = "r1", created = now, model = "m",
            choices = listOf(
                DeepSeekResponse.Choice(
                    index = 0,
                    message = DeepSeekMessage.AssistantMessage(content = "ok"),
                    finishReason = DeepSeekFinishReason.STOP
                )
            ),
            usage = DeepSeekUsage(
                totalTokens = 200, promptTokens = 100, completionTokens = 100,
                completionTokensDetails = DeepSeekUsage.CompletionTokensDetails(reasoningTokens = 30)
            )
        )
        assertEquals(30, mapToChatResult(response).usage?.reasoningTokens)
    }
    
    // endregion
    
    // region mapChunkToChatResult
    
    @Test
    fun `mapChunkToChatResult maps stream chunk`() {
        val chunk = DeepSeekStreamChunk(
            id = "chunk-1", created = now, model = "deepseek",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(
                        content = "partial", reasoningContent = "thinking..."
                    ),
                    finishReason = null
                )
            )
        )
        val result = mapChunkToChatResult(chunk)
        assertEquals("partial", result.message?.content)
        assertEquals("thinking...", (result.message as ChatMessage.AssistantMessage).reasoningContent)
        assertNull(result.finishReason)
    }
    
    @Test
    fun `mapChunkToChatResult includes usage from chunk`() {
        val chunk = DeepSeekStreamChunk(
            id = "chunk-1", created = now, model = "deepseek",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(),
                    finishReason = DeepSeekFinishReason.STOP
                )
            ),
            usage = DeepSeekUsage(
                completionTokens = 60,
                promptTokens = 40,
                totalTokens = 100,
                promptCacheHitTokens = 10,
                promptCacheMissTokens = 30
            )
        )
        val result = mapChunkToChatResult(chunk)
        assertEquals(100, result.usage?.totalTokens)
        assertEquals(10, result.usage?.cacheHitTokens)
        assertEquals(30, result.usage?.cacheMissTokens)
    }
    
    @Test
    fun `mapChunkToChatResult includes reasoning tokens from chunk usage`() {
        val chunk = DeepSeekStreamChunk(
            id = "chunk-1", created = now, model = "deepseek",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(content = "ok"),
                    finishReason = DeepSeekFinishReason.STOP
                )
            ),
            usage = DeepSeekUsage(
                totalTokens = 200, promptTokens = 100, completionTokens = 100,
                completionTokensDetails = DeepSeekUsage.CompletionTokensDetails(reasoningTokens = 40)
            )
        )
        assertEquals(40, mapChunkToChatResult(chunk).usage?.reasoningTokens)
    }
    
    // endregion
    
    // region extractToolCalls
    
    @Test
    fun `extractToolCalls extracts fragments from chunk`() {
        val chunk = DeepSeekStreamChunk(
            id = "c1", created = now, model = "m",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(
                        toolCalls = listOf(
                            DeepSeekStreamChunk.Choice.ToolCall(
                                index = 0, id = "call-1",
                                function = DeepSeekStreamChunk.Choice.ToolCall.Function(
                                    name = "read_file", arguments = "{\"path\":\"/tmp\"}"
                                )
                            )
                        )
                    ),
                    finishReason = null
                )
            )
        )
        val fragments = extractToolCalls(chunk)
        assertNotNull(fragments)
        assertEquals(1, fragments.size)
    }
    
    @Test
    fun `extractToolCalls returns null when empty`() {
        val chunk = DeepSeekStreamChunk(
            id = "c1", created = now, model = "m",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(),
                    finishReason = null
                )
            )
        )
        assertNull(extractToolCalls(chunk))
    }
    
    @Test
    fun `extractToolCalls returns null for empty choices`() {
        val chunk = DeepSeekStreamChunk(
            id = "c1", created = now, model = "m",
            choices = emptyList()
        )
        assertNull(extractToolCalls(chunk))
    }
    
    @Test
    fun `extractToolCalls handles null function`() {
        val chunk = DeepSeekStreamChunk(
            id = "c1", created = now, model = "m",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(
                        toolCalls = listOf(
                            DeepSeekStreamChunk.Choice.ToolCall(
                                index = 0, id = null, function = null
                            )
                        )
                    ),
                    finishReason = null
                )
            )
        )
        val fragments = extractToolCalls(chunk)
        assertNotNull(fragments)
        assertEquals(1, fragments.size)
    }
    
    // endregion
    
    // region boundary tests for branch coverage
    
    @Test
    fun `createRequestBody with thinking false`() {
        val userMsg = ChatMessage.UserMessage("hi", now)
        val request = ChatRequest(model = "test", messages = listOf(userMsg), thinking = false)
        val body = createRequestBody(request)
        assertEquals(OpenAiRequest.Thinking.Type.DISABLED, body.thinking?.type)
    }
    
    @Test
    fun `createRequestBody with thinking null`() {
        val userMsg = ChatMessage.UserMessage("hi", now)
        val request = ChatRequest(model = "test", messages = listOf(userMsg), thinking = null)
        val body = createRequestBody(request)
        assertNull(body.thinking)
    }
    
    @Test
    fun `createRequestBody with AssistantMessage without tool calls`() {
        val assistant = ChatMessage.AssistantMessage(content = "reply", createdAt = now)
        val request = ChatRequest(model = "test", messages = listOf(assistant))
        val body = createRequestBody(request)
        val msg = body.messages[0] as DeepSeekMessage.AssistantMessage
        assertEquals("reply", msg.content)
        assertNull(msg.toolCalls)
    }
    
    @Test
    fun `mapChunkToChatResult with empty choices`() {
        val chunk = DeepSeekStreamChunk(
            id = "chunk-1", created = now, model = "m",
            choices = emptyList()
        )
        val result = mapChunkToChatResult(chunk)
        assertNull(result.message?.content)
    }
    
    @Test
    fun `mapChunkToChatResult with null delta content`() {
        val chunk = DeepSeekStreamChunk(
            id = "chunk-1", created = now, model = "m",
            choices = listOf(
                DeepSeekStreamChunk.Choice(
                    index = 0,
                    delta = DeepSeekStreamChunk.Choice.Delta(
                        content = null, reasoningContent = null
                    ),
                    finishReason = null
                )
            )
        )
        val result = mapChunkToChatResult(chunk)
        assertNull(result.message?.content)
        assertNull((result.message as ChatMessage.AssistantMessage).reasoningContent)
    }
    
    // endregion
}
