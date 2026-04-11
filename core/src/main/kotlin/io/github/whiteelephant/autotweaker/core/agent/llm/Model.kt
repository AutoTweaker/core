package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.Price
import io.github.whiteelephant.autotweaker.core.Url

data class Model(
    val name: String,
    val provider: Provider,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val price: TokenPrice,

    val supportsStreaming: Boolean,
    val supportsToolCalls: Boolean,
    val supportsReasoning: Boolean,
    val supportsImage: Boolean,
)

data class TokenPrice(
    val inputPrice: List<PriceTier>,
    val outputPrice: List<PriceTier>,
) {
    data class PriceTier(
        val fromTokens: Int,
        val toTokens: Int? = null,
        val price: Price,
        val cachedPrice: Price? = null
    )
}

data class Provider(
    val name: String,
    val baseUrl: Url,
    val apiKey: String,
    val errorHandlingRules: List<ErrorHandlingRule>
) {
    data class ErrorHandlingRule(
        val statusCode: Int,
        val strategy: RecoveryStrategy
    ) {
        enum class RecoveryStrategy {
            RETRY,
            FALLBACK,
            CONTEXT_FALLBACK,
            PROVIDER_FALLBACK,
        }
    }
}
