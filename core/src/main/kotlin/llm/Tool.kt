package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonElement
)
