package io.github.whiteelephant.autotweaker.core

import kotlinx.serialization.Serializable

import java.math.BigDecimal
import java.util.Currency

@Serializable
data class Price(
    @Serializable(BigDecimalSerializer::class)
    val amount: BigDecimal,
    @Serializable(CurrencySerializer::class)
    val currency: Currency,
)
