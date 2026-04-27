package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Provider.ErrorHandlingRule
import io.github.autotweaker.core.Provider.Model.ModelInfo
import io.github.autotweaker.core.Url
import kotlinx.coroutines.flow.Flow

interface LlmClient {
	val providerInfo: ProviderInfo
	
	data class ProviderInfo(
		val name: String,
		val baseUrl: Url,
		val models: List<ModelInfo>,
		val errorHandlingRules: List<ErrorHandlingRule>
	)
	
	suspend fun chat(request: ChatRequest, apiKey: String, baseUrl: Url? = null): Flow<ChatResult>
}
