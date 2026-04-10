package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.llm.*
import io.ktor.client.*

class MiMoClientFactory : LlmClientFactory {
    override val name: String
        get() = "mimo"

    override fun create(apiKey: String, httpClient: HttpClient, baseUrl: Url?): LlmClient {
        return if (baseUrl != null) MiMoClient(apiKey, httpClient, baseUrl) else MiMoClient(apiKey, httpClient)
    }
}
