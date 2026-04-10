package io.github.whiteelephant.autotweaker.core.agent.llm

import java.math.BigDecimal

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
    val inputPrice: List<Price>,
    val outputPrice: List<Price>,
    val currency: String = "USD",
) {
    data class Price(
        val fromTokens: Int,
        val toTokens: Int? = null,
        val price: BigDecimal,
        val cachedPrice: BigDecimal? = null
    )
}

data class Provider(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val errorHandlingRules: List<ErrorHandlingRule>
) {
    data class ErrorHandlingRule(
        val statusCode: Int,
        val strategy: RecoveryStrategy
    ) {
        enum class RecoveryStrategy {
            Retry,
            Fallback,
            ImageFallback,
            ContextFallback,
            ProviderFallback,
        }
    }
}
