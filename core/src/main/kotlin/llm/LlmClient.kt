package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

interface LlmClient {
    suspend fun chat(request: ChatRequest): ChatResult
    suspend fun chatStream(request: ChatRequest): Flow<ChatResult>
}
