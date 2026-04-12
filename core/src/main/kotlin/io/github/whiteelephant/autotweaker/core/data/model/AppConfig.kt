package io.github.whiteelephant.autotweaker.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val toolsConfig: ToolsConfig = ToolsConfig(),
) {
    @Serializable
    data class ToolsConfig(
        val read: Read = Read(),
    ) {
        @Serializable
        data class Read(
            val maxReadSize: Int = 32000,
            val maxReadLines: Int = 500,
        )
    }
}
