package io.github.whiteelephant.autotweaker.core.agent.llm

import io.github.whiteelephant.autotweaker.core.Price
import io.github.whiteelephant.autotweaker.core.Url

import io.github.whiteelephant.autotweaker.core.data.model.Provider.ErrorHandlingRule
import io.github.whiteelephant.autotweaker.core.data.model.Provider.Model.TokenPrice

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

data class Provider(
    val name: String,
    val baseUrl: Url,
    val apiKey: String,
    val errorHandlingRules: List<ErrorHandlingRule>
)
