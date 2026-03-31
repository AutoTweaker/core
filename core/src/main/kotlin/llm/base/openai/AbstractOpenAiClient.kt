package io.github.whiteelephant.autotweaker.core.llm.base.openai

import io.github.whiteelephant.autotweaker.core.llm.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.flow

abstract class AbstractOpenAiClient(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val httpClient: HttpClient,
    // 忽略未知字段的 Json 实例，应对不同厂商的冗余返回
    protected val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
) : LlmClient {
    /** * 钩子 1：将业务层的 ChatRequest 转换为发送给 API 的数据对象。
     * 返回 Any 是为了灵活性，子类可以返回一个专门的 Data Class。
     */
    protected abstract fun createRequestBody(request: ChatRequest): Any

    /** * 钩子 2：处理非流式响应。
     * 将 API 返回的标准响应（OpenAiResponse）转换为你接口定义的 ChatResult。
     */
    protected abstract fun mapToChatResult(response: OpenAiResponse): ChatResult

    /** * 钩子 3：处理流式响应。
     * 将流中返回的每一个数据切片（OpenAiStreamChunk）转换为 ChatResult。
     */
    protected abstract fun mapChunkToChatResult(chunk: OpenAiStreamChunk): ChatResult


}
