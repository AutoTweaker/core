package io.github.autotweaker.core.llm.provider.mimo

import io.github.autotweaker.core.llm.base.openai.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MiMoToolCall(
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
