package io.github.whiteelephant.autotweaker.core.llm

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)
