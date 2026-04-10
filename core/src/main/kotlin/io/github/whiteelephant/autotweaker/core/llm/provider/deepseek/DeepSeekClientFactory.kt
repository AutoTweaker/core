package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.llm.*
import io.ktor.client.*

class DeepSeekClientFactory : LlmClientFactory {
    override val name: String
        get() = "deepseek"

    override fun create(apiKey: String, httpClient: HttpClient, baseUrl: Url?): LlmClient {
        return if (baseUrl != null) DeepSeekClient(apiKey, httpClient, baseUrl) else DeepSeekClient(apiKey, httpClient)
    }
}
