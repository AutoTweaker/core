package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import java.math.BigDecimal

//TODO
data class Request(
    val provider: String,
    val request: ChatRequest,
)

data class Model(
    val provider: String,
    val modelName: String,
    val contentWindow: Int,
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
    val tokenUnit: Int = 1000000
) {
    data class Price(
        val fromTokens: Int,
        val toTokens: Int? = null,
        val price: BigDecimal
    )
}
