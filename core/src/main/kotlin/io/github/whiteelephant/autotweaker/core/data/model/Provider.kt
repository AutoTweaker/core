package io.github.whiteelephant.autotweaker.core.data.model

import io.github.whiteelephant.autotweaker.core.Price
import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    val name: String,
    val providerType: String,
    val apiKey: String,
    val baseUrl: String,
    val models: List<Model>,
    val errorHandlingRules: List<ErrorHandlingRule>
) {
    @Serializable
    data class ErrorHandlingRule(
        val statusCode: Int,
        val strategy: RecoveryStrategy
    ) {
        @Serializable
        enum class RecoveryStrategy {
            RETRY,
            FALLBACK,
            CONTEXT_FALLBACK,
            PROVIDER_FALLBACK,
        }
    }

    @Serializable
    data class Model(
        val name: String,

        val contextWindow: Int,
        val maxOutputTokens: Int,
        val price: TokenPrice,

        val supportsStreaming: Boolean,
        val supportsToolCalls: Boolean,
        val supportsReasoning: Boolean,
        val supportsImage: Boolean,
    ) {
        @Serializable
        data class TokenPrice(
            val inputPrice: List<PriceTier>,
            val outputPrice: List<PriceTier>,
        ) {
            @Serializable
            data class PriceTier(
                val fromTokens: Int,
                val toTokens: Int? = null,
                val price: Price,
                val cachedPrice: Price? = null
            )
        }

    }
}
