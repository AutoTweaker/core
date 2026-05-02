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

package io.github.autotweaker.core.llm.base.openai

import io.github.autotweaker.core.Url
import io.github.autotweaker.core.llm.ChatMessage
import io.github.autotweaker.core.llm.ChatRequest
import io.github.autotweaker.core.llm.ChatResult
import io.github.autotweaker.core.llm.provider.deepseek.DeepSeekClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Clock

class AbstractOpenAiClientChatTest {
    
    private val now = Clock.System.now()
    private val serializationJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
    
    private fun createMockHttpClientEngine(responseContent: String, status: HttpStatusCode = HttpStatusCode.OK) =
        MockEngine { _ ->
            respond(
                content = ByteReadChannel(responseContent),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
    
    private fun injectHttpClient(engine: MockEngine) {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(serializationJson) }
        }
        val field = AbstractOpenAiClient::class.java.getDeclaredField("sharedHttpClient")
        field.isAccessible = true
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val putObjectMethod =
            unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
        val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            .invoke(unsafe, field) as Long
        putObjectMethod.invoke(unsafe, base, offset, httpClient)
    }
    
    private fun userRequest() = ChatRequest(
        model = "deepseek-v4-pro",
        messages = listOf(ChatMessage.UserMessage("hello", now))
    )
    
    private fun streamRequest() = userRequest().copy(stream = true)
    
    // region non-streaming
    
    @Test
    fun `non-streaming chat returns assistant message`() = runTest {
        val responseJson = """{
            "id":"resp-1","created":1715678901,"model":"deepseek-v4-pro",
            "choices":[{"index":0,"message":{"role":"assistant","content":"hello world"},"finish_reason":"stop"}],
            "usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}
        }"""
        injectHttpClient(createMockHttpClientEngine(responseJson))
        
        val client = DeepSeekClient()
        val results = client.chat(userRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertEquals("hello world", results[0].message?.content)
        assertEquals(ChatResult.FinishReason.Type.STOP, results[0].finishReason?.type)
        assertEquals(30, results[0].usage?.totalTokens)
    }
    
    @Test
    fun `non-streaming chat returns error on non-success status`() = runTest {
        injectHttpClient(createMockHttpClientEngine("{}", HttpStatusCode.InternalServerError))
        
        val client = DeepSeekClient()
        val results = client.chat(userRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertIs<ChatMessage.ErrorMessage>(results[0].message)
        assertEquals(HttpStatusCode.InternalServerError, (results[0].message as ChatMessage.ErrorMessage).statusCode)
    }
    
    // endregion
    
    // region streaming
    
    @Test
    fun `streaming chat returns content chunks`() = runTest {
        val sseData = buildStreamResponse(
            """{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"role":"assistant","content":"hello"},"finish_reason":null}]}""",
            """{"id":"c2","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""",
            """{"id":"c3","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":""},"finish_reason":"stop"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}"""
        )
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertTrue(results.size >= 3)
        assertEquals("hello", results[0].message?.content)
        assertEquals(" world", results[1].message?.content)
        assertEquals(ChatResult.FinishReason.Type.STOP, results.last().finishReason?.type)
        assertEquals(30, results.last().usage?.totalTokens)
    }
    
    @Test
    fun `streaming chat assembles tool calls`() = runTest {
        val sseData = buildStreamResponse(
            """{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"read_file","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}"""
        )
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        val last = results.last()
        assertEquals(ChatResult.FinishReason.Type.TOOL, last.finishReason?.type)
        val assistantMsg = last.message as ChatMessage.AssistantMessage
        assertNotNull(assistantMsg.toolCalls)
        assertEquals(1, assistantMsg.toolCalls.size)
        assertEquals("call-1", assistantMsg.toolCalls[0].id)
        assertEquals("read_file", assistantMsg.toolCalls[0].name)
    }
    
    @Test
    fun `streaming chat handles stream error status`() = runTest {
        injectHttpClient(createMockHttpClientEngine("", HttpStatusCode.BadGateway))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertIs<ChatMessage.ErrorMessage>(results[0].message)
    }
    
    @Test
    fun `streaming chat handles parse error gracefully`() = runTest {
        val sseData = "data: invalid json\r\n\r\n"
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertIs<ChatMessage.ErrorMessage>(results[0].message)
    }
    
    @Test
    fun `streaming chat handles network exception`() = runTest {
        val errorEngine = MockEngine {
            throw java.io.IOException("connection refused")
        }
        injectHttpClient(errorEngine)
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertIs<ChatMessage.ErrorMessage>(results[0].message)
        assertTrue(results[0].message!!.content!!.contains("connection refused"))
    }
    
    @Test
    fun `streaming chat ignores non-data lines`() = runTest {
        val sseData = """:heartbeat
data: {"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":"stop"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}
"""
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertEquals(1, results.size)
        assertEquals("ok", results[0].message?.content)
    }
    
    @Test
    fun `streaming chat handles DONE signal`() = runTest {
        val sseData = "data: [DONE]\r\n\r\n"
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertTrue(results.isEmpty())
    }
    
    @Test
    fun `streaming chat with empty data lines`() = runTest {
        val sseData = "data: \r\n\r\ndata: [DONE]\r\n\r\n"
        injectHttpClient(createMockHttpClientEngine(sseData))
        
        val client = DeepSeekClient()
        val results = client.chat(streamRequest(), "test-key", Url("https://mock.test/v1")).toList()
        
        assertTrue(results.isEmpty())
    }
    
    // endregion
    
    companion object {
        private fun buildStreamResponse(vararg chunks: String): String =
            chunks.joinToString("\r\n\r\n") { "data: $it\r\n" }.plus("\r\ndata: [DONE]\r\n\r\n")
    }
}
