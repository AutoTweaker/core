package io.github.whiteelephant.autotweaker.core.llm

import io.ktor.http.CacheControl

data class Usage(
    val totalTokens: Int,
    val promptTokens: Int,
    val completionTokens: Int,

    val reasoningTokens: Int? = null,
    val cacheHitTokens: Int? = null,
    val cacheMissTokens: Int? = null
)
