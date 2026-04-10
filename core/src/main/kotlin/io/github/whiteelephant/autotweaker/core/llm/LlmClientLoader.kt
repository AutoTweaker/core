package io.github.whiteelephant.autotweaker.core.llm

import io.ktor.client.*
import java.util.ServiceLoader

object LlmClientLoader {
    fun load(name: String, apiKey: String, httpClient: HttpClient, baseUrl: String? = null): LlmClient {
        val factory = ServiceLoader.load(LlmClientFactory::class.java)
            .firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Unknown LLM provider: $name")
        return factory.create(apiKey, httpClient, baseUrl)
    }

    fun availableProviders(): List<String> {
        return ServiceLoader.load(LlmClientFactory::class.java).map { it.name }
    }
}
