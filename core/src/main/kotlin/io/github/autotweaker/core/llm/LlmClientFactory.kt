package io.github.autotweaker.core.llm

import io.github.autotweaker.core.Url
import io.ktor.client.*

interface LlmClientFactory {
	val name: String
	
	fun create(
		apiKey: String, httpClient: HttpClient, baseUrl: Url? = null
	): LlmClient
}
