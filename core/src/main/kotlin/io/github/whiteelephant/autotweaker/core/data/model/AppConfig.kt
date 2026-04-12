package io.github.whiteelephant.autotweaker.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val toolsConfig: ToolsConfig = ToolsConfig(),
    val providers: List<Provider> = emptyList(),
)
