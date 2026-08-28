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

package io.github.autotweaker.core.infrastructure.llm.base.openai

import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatRequest
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.llm.openai.AbstractOpenAiClient
import io.github.autotweaker.core.infrastructure.llm.provider.deepseek.DeepSeekClient
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
		// 先完成类初始化，避免 <clinit> 在替换后覆盖 sharedHttpClient
		Class.forName(AbstractOpenAiClient::class.java.name)
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
		instructions = null,
		messages = listOf(ChatMessage.User(listOf(ContentPart.Text("hello")), now)),
		reasoning = null,
		stream = false,
		maxTokens = null,
		tools = null,
		temperature = null,
		jsonOutput = null
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
		val results = client.chat(userRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		val assembled = assertIs<ChatResult.Assembled>(results[0])
		assertEquals("hello world", assembled.message.content)
		assertEquals(30, assembled.usage?.totalTokens)
	}
	
	@Test
	fun `non-streaming chat returns error on non-success status`() = runTest {
		injectHttpClient(createMockHttpClientEngine("{}", HttpStatusCode.InternalServerError))
		
		val client = DeepSeekClient()
		val results = client.chat(userRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		val failed = assertIs<ChatResult.Failed>(results[0])
		assertEquals(500, failed.statusCode)
		assertTrue(failed.message!!.contains("LLM API Error"))
	}
	
	// endregion
	
	// region streaming
	
	@Test
	fun `streaming chat returns accumulated content`() = runTest {
		val sseData = buildStreamResponse(
			"""{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"role":"assistant","content":"hello"},"finish_reason":null}]}""",
			"""{"id":"c2","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""",
			"""{"id":"c3","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":""},"finish_reason":"stop"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}"""
		)
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(4, results.size)
		val chunks = results.filterIsInstance<ChatResult.Chunk>()
		assertEquals(3, chunks.size)
		assertEquals("hello", chunks[0].content)
		assertEquals(" world", chunks[1].content)
		assertEquals("", chunks[2].content)
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		assertEquals("hello world", assembled.message.content)
		assertEquals(30, assembled.usage?.totalTokens)
	}
	
	@Test
	fun `streaming chat assembles tool calls`() = runTest {
		val sseData = buildStreamResponse(
			"""{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"read_file","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}"""
		)
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(2, results.size)
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		val toolCalls = assembled.message.toolCalls
		assertNotNull(toolCalls)
		assertEquals(1, toolCalls.size)
		assertEquals("call-1", toolCalls[0].id)
		assertEquals("read_file", toolCalls[0].name)
	}
	
	@Test
	fun `streaming chat handles stream error status`() = runTest {
		injectHttpClient(createMockHttpClientEngine("", HttpStatusCode.BadGateway))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		val failed = assertIs<ChatResult.Failed>(results[0])
		assertTrue(failed.message!!.contains("502"))
	}
	
	@Test
	fun `streaming chat handles parse error gracefully`() = runTest {
		val sseData = "data: invalid json\r\n\r\n"
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		assertIs<ChatResult.Failed>(results[0])
	}
	
	@Test
	fun `streaming chat handles network exception`() = runTest {
		val errorEngine = MockEngine {
			throw java.io.IOException("connection refused")
		}
		injectHttpClient(errorEngine)
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		val failed = assertIs<ChatResult.Failed>(results[0])
		assertTrue(failed.message!!.contains("connection refused"))
	}
	
	@Test
	fun `streaming chat ignores non-data lines`() = runTest {
		val sseData = """:heartbeat
data: {"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":"stop"}],"usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}}
"""
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(2, results.size)
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		assertEquals("ok", assembled.message.content)
	}
	
	@Test
	fun `streaming chat handles DONE signal`() = runTest {
		val sseData = "data: [DONE]\r\n\r\n"
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		val assembled = assertIs<ChatResult.Assembled>(results[0])
		assertEquals("", assembled.message.content)
	}
	
	@Test
	fun `streaming chat with empty data lines`() = runTest {
		val sseData = "data: \r\n\r\ndata: [DONE]\r\n\r\n"
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		assertIs<ChatResult.Assembled>(results[0])
	}
	
	@Test
	fun `streaming chat handles readLine returning null`() = runTest {
		val sseData =
			"data: {\"id\":\"c1\",\"created\":1715678901,\"model\":\"m\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}"
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		assertEquals(2, results.size)
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		assertEquals("partial", assembled.message.content)
	}
	
	@Test
	fun `streaming tool call fragment with null id`() = runTest {
		val sseData = buildStreamResponse(
			"""{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"type":"function","function":{"name":"f1","arguments":"a1"}}]},"finish_reason":"tool_calls"}],"usage":{"completion_tokens":1,"prompt_tokens":1,"total_tokens":2}}"""
		)
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		val tc = assembled.message.toolCalls
		assertNotNull(tc)
		assertEquals("f1", tc[0].name)
		assertEquals("a1", tc[0].arguments)
	}
	
	@Test
	fun `streaming tool call fragment with null name`() = runTest {
		val sseData = buildStreamResponse(
			"""{"id":"c1","created":1715678901,"model":"m","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"tc1","type":"function","function":{"arguments":"a1"}}]},"finish_reason":"tool_calls"}],"usage":{"completion_tokens":1,"prompt_tokens":1,"total_tokens":2}}"""
		)
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		val assembled = assertIs<ChatResult.Assembled>(results.last())
		val tc = assembled.message.toolCalls
		assertNotNull(tc)
		assertEquals("tc1", tc[0].id)
	}
	
	@Test
	fun `streaming chunk parse error with null exception message`() = runTest {
		val sseData = "data: {\"invalid\"\r\n\r\n"
		injectHttpClient(createMockHttpClientEngine(sseData))
		
		val client = DeepSeekClient()
		val results = client.chat(streamRequest(), "test-key", "https://mock.test/v1".toUrl()).toList()
		
		assertEquals(1, results.size)
		assertIs<ChatResult.Failed>(results[0])
	}
	
	// endregion
	
	companion object {
		init {
			TestServices.init()
		}
		
		private fun buildStreamResponse(vararg chunks: String): String =
			chunks.joinToString("\r\n\r\n") { "data: $it\r\n" }.plus("\r\ndata: [DONE]\r\n\r\n")
	}
}
