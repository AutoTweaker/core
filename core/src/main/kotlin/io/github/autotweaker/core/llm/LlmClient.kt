package io.github.autotweaker.core.llm

import kotlinx.coroutines.flow.Flow

interface LlmClient {
	suspend fun chat(request: ChatRequest): Flow<ChatResult>
}
