package io.github.whiteelephant.autotweaker.core.llm.provider.mimo

import com.google.auto.service.AutoService
import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.llm.*
import io.ktor.client.*

@AutoService(LlmClientFactory::class)
class MiMoClientFactory : LlmClientFactory {
    override val name: String
        get() = "mimo"

    override fun create(apiKey: String, httpClient: HttpClient, baseUrl: Url?): LlmClient {
        return if (baseUrl != null) MiMoClient(apiKey, httpClient, baseUrl) else MiMoClient(apiKey, httpClient)
    }
}
