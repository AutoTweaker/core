package io.github.whiteelephant.autotweaker.core.llm.provider.deepseek

import io.github.whiteelephant.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: Function
) {
    @Serializable
    data class Function(
        val name: String,
        val arguments: String
    )
}
