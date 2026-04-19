package io.github.autotweaker.core.llm

data class Usage(
	val totalTokens: Int,
	val promptTokens: Int,
	val completionTokens: Int,
	
	val reasoningTokens: Int? = null,
	val cacheHitTokens: Int? = null,
	val cacheMissTokens: Int? = null,
	
	val imageTokens: Int? = null
)
