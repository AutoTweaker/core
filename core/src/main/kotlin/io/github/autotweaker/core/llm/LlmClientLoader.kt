package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Url
import io.ktor.client.*
import java.util.*

object LlmClientLoader {
	fun load(name: String, apiKey: String, httpClient: HttpClient, baseUrl: Url? = null): LlmClient {
		val factory = ServiceLoader.load(LlmClientFactory::class.java)
			.firstOrNull { it.name == name }
			?: throw IllegalArgumentException("Unknown LLM provider: $name")
		return factory.create(apiKey, httpClient, baseUrl)
	}
	
	@Suppress("unused")
	fun availableProviders(): List<String> {
		return ServiceLoader.load(LlmClientFactory::class.java).map { it.name }
	}
}
