package io.github.autotweaker.core.llm

import java.util.*

object LlmClientLoader {
	fun load(name: String): LlmClient {
		return ServiceLoader.load(LlmClient::class.java)
			.firstOrNull { it.providerInfo.name == name }
			?: throw IllegalArgumentException("Unknown LLM provider: $name")
	}
	
	@Suppress("unused")
	fun availableProviders(): List<String> {
		return ServiceLoader.load(LlmClient::class.java).map { it.providerInfo.name }
	}
}