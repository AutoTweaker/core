package io.github.whiteelephant.autotweaker.core

import java.math.BigDecimal
import java.util.Currency

data class Price(
    val amount: BigDecimal,
    val currency: Currency,
)
