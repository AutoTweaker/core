package io.github.autotweaker.core

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.*

@Serializable
data class Price(
	@Serializable(BigDecimalSerializer::class)
	val amount: BigDecimal,
	@Serializable(CurrencySerializer::class)
	val currency: Currency,
)
