package io.github.whiteelephant.autotweaker.core.llm

data class Usage(
    val totalTokens: Int,
    val contextWindowUsage: Double,
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double
)
