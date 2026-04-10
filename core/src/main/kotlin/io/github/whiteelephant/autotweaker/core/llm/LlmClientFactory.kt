package io.github.whiteelephant.autotweaker.core.llm

import io.ktor.client.*

interface LlmClientFactory {
    val name: String

    fun create(
        apiKey: String, httpClient: HttpClient, baseUrl: String? = null
    ): LlmClient
}
