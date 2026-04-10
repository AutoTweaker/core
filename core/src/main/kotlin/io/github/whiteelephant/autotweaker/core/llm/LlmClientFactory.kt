package io.github.whiteelephant.autotweaker.core.llm

import io.github.whiteelephant.autotweaker.core.Url
import io.ktor.client.*

interface LlmClientFactory {
    val name: String

    fun create(
        apiKey: String, httpClient: HttpClient, baseUrl: Url? = null
    ): LlmClient
}
