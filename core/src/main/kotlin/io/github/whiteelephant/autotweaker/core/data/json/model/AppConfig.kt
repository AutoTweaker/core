package io.github.whiteelephant.autotweaker.core.data.json.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val providers: List<Provider> = emptyList(),
)
