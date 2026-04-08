package io.github.whiteelephant.autotweaker.core.llm.base.openai

import io.github.whiteelephant.autotweaker.core.llm.ChatMessage
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
        Request : OpenAiRequest,
        Response : OpenAiResponse,
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

        private fun buildToolCalls(
            pendingToolCalls: Map<Int, PendingToolCall>
        ): List<ChatMessage.AssistantMessage.ToolCall>? {
            if (pendingToolCalls.isEmpty()) return null
            return pendingToolCalls.toSortedMap().values.map { it.toToolCall() }
        }
    }

    // ---------- 流式 tool call 累积的中间表示 ----------

    data class ToolCallFragment(
        val index: Int,
        val id: String?,
        val name: String?,
        val arguments: String?
    )

    class PendingToolCall(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun toToolCall() = ChatMessage.AssistantMessage.ToolCall(id, name, arguments.toString())
    }

    // ---------- 子类需实现的抽象方法 ----------

    /** 将业务层的 ChatRequest 转换为发送给 API 的数据对象 */
    protected abstract fun createRequestBody(request: ChatRequest): Request

    /** 处理非流式响应，将 API 返回的完整响应转换为 ChatResult */
    protected abstract fun mapToChatResult(response: Response): ChatResult

    /** 处理流式响应中的单个 chunk，提取 content/reasoningContent（不含 tool call） */
    protected abstract fun mapChunkToChatResult(chunk: Chunk): ChatResult

    /** 从流式 chunk 中提取 tool call 碎片列表，供基类拼接 */
    protected abstract fun extractToolCalls(chunk: Chunk): List<ToolCallFragment>?


    override suspend fun chat(request: ChatRequest): Flow<ChatResult> = flow {
        try {
            if (request.stream) {
                // 流式逻辑
                val body = createRequestBody(request)

                httpClient.preparePost("$baseUrl/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(body, requestTypeInfo)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        emit(ChatResult(
                            message = ChatMessage.ErrorMessage(
                                content = "LLM Stream Error: ${response.status}",
                                createdAt = System.currentTimeMillis(),
                                error = ChatMessage.ErrorMessage.Error.StatusCode(response.status.value)
                            ),
                            finishReason = null,
                            usage = null
                        ))
                        return@execute
                    }

                    val channel = response.bodyAsChannel()
                    val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()

                    try {
                        while (!channel.isClosedForRead) {
                            val line = channel.readLine() ?: break

                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()

                                if (data == "[DONE]") break

                                if (data.isNotEmpty()) {
                                    try {
                                        val chunk = json.decodeFromString(chunkSerializer, data)

                                        // 累积 tool call 碎片
                                        extractToolCalls(chunk)?.forEach { fragment ->
                                            val pending = pendingToolCalls.getOrPut(fragment.index) { PendingToolCall() }
                                            if (fragment.id != null) pending.id = fragment.id
                                            if (fragment.name != null) pending.name = fragment.name
                                            if (fragment.arguments != null) pending.arguments.append(fragment.arguments)
                                        }

                                        // 构建 ChatResult（content/reasoningContent 由子类处理，finishReason 也由子类提取）
                                        var result = mapChunkToChatResult(chunk)

                                        // 当 finishReason 不为空时，将累积的 toolCalls 附加到消息上
                                        if (result.finishReason != null) {
                                            val toolCalls = buildToolCalls(pendingToolCalls)
                                            if (toolCalls != null) {
                                                val msg = result.message as? ChatMessage.AssistantMessage
                                                if (msg != null) {
                                                    result = result.copy(
                                                        message = msg.copy(toolCalls = toolCalls)
                                                    )
                                                }
                                            }
                                        }

                                        emit(result)
                                    } catch (e: Throwable) {
                                        emit(ChatResult(
                                            message = ChatMessage.ErrorMessage(
                                                content = e.message ?: "Failed to parse stream chunk",
                                                createdAt = System.currentTimeMillis(),
                                                error = ChatMessage.ErrorMessage.Error.from(e)
                                            ),
                                            finishReason = null,
                                            usage = null
                                        ))
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        emit(ChatResult(
                            message = ChatMessage.ErrorMessage(
                                content = e.message ?: "Stream read error",
                                createdAt = System.currentTimeMillis(),
                                error = ChatMessage.ErrorMessage.Error.from(e)
                            ),
                            finishReason = null,
                            usage = null
                        ))
                    }
                }
            } else {
                // 非流式逻辑
                val response = httpClient.post("$baseUrl/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(createRequestBody(request), requestTypeInfo)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    emit(ChatResult(
                        message = ChatMessage.ErrorMessage(
                            content = "LLM API Error (${response.status}): $errorBody",
                            createdAt = System.currentTimeMillis(),
                            error = ChatMessage.ErrorMessage.Error.StatusCode(response.status.value)
                        ),
                        finishReason = null,
                        usage = null
                    ))
                    return@flow
                }

                val openAiResponse = response.body<Response>(responseTypeInfo)
                emit(mapToChatResult(openAiResponse))
            }
        } catch (e: Throwable) {
            emit(ChatResult(
                message = ChatMessage.ErrorMessage(
                    content = e.message ?: "Unknown error",
                    createdAt = System.currentTimeMillis(),
                    error = ChatMessage.ErrorMessage.Error.from(e)
                ),
                finishReason = null,
                usage = null
            ))
        }
    }
}
