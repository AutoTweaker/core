package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Url
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.ModelInfo
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
