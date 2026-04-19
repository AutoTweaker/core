package io.github.autotweaker.core.llm.provider.deepseek

import com.google.auto.service.AutoService
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.llm.LlmClient
import io.github.autotweaker.core.llm.LlmClientFactory
import io.ktor.client.*

@AutoService(LlmClientFactory::class)
class DeepSeekClientFactory : LlmClientFactory {
	override val name: String
		get() = "deepseek"
	
	override fun create(apiKey: String, httpClient: HttpClient, baseUrl: Url?): LlmClient {
		return if (baseUrl != null) DeepSeekClient(apiKey, httpClient, baseUrl) else DeepSeekClient(apiKey, httpClient)
	}
}
