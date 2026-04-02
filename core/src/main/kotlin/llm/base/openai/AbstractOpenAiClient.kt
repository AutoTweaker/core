package io.github.whiteelephant.autotweaker.core.llm.base.openai

import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import io.github.whiteelephant.autotweaker.core.llm.ChatResult
import io.github.whiteelephant.autotweaker.core.llm.LlmClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

abstract class AbstractOpenAiClient<
        Request : OpenAiRequest<*>,
        Response : OpenAiResponse<*>,
        Chunk : OpenAiStreamChunk>(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val httpClient: HttpClient,
    private val requestTypeInfo: TypeInfo,
    private val responseTypeInfo: TypeInfo,
    private val chunkSerializer: KSerializer<Chunk>,
) : LlmClient {
    companion object {
        private val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    /** * 钩子 1：将业务层的 ChatRequest 转换为发送给 API 的数据对象。
     * 返回 Any 是为了灵活性，子类可以返回一个专门的 Data Class。
     */
    protected abstract fun createRequestBody(request: ChatRequest, stream: Boolean): Request

    /** * 钩子 2：处理非流式响应。
     * 将 API 返回的标准响应（OpenAiResponse）转换为你接口定义的 ChatResult。
     */
    protected abstract fun mapToChatResult(response: Response): ChatResult

    /** * 钩子 3：处理流式响应。
     * 将流中返回的每一个数据切片（OpenAiStreamChunk）转换为 ChatResult。
     */
    protected abstract fun mapChunkToChatResult(chunk: Chunk): ChatResult


    override suspend fun chat(request: ChatRequest): ChatResult {
        val response = httpClient.post("$baseUrl/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            // 调用钩子：让子类决定具体的 JSON 结构
            setBody(createRequestBody(request, stream = false), requestTypeInfo)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw IllegalStateException("LLM API Error (${response.status}): $errorBody")
        }

        // 解析为 OpenAI 标准响应
        val openAiResponse = response.body<Response>(responseTypeInfo)

        // 调用钩子：将标准响应转为你的 ChatResult
        return mapToChatResult(openAiResponse)
    }


    override suspend fun chatStream(request: ChatRequest): Flow<ChatResult> = flow {
        // 强制开启 stream 模式
        val body = createRequestBody(request, stream = true)

        httpClient.preparePost("$baseUrl/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body, requestTypeInfo)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw IllegalStateException("LLM Stream Error: ${response.status}")
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                // 逐行读取 SSE 数据
                val line = channel.readLine() ?: break

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()

                    // 结束信号
                    if (data == "[DONE]") break

                    if (data.isNotEmpty()) {
                        // 解析当前的小块数据
                        val chunk = json.decodeFromString(chunkSerializer, data)
                        // 调用钩子：转换为 ChatResult 并发射出去
                        emit(mapChunkToChatResult(chunk))
                    }
                }
            }
        }
    }
}
