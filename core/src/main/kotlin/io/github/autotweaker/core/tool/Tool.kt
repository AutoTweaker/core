package io.github.autotweaker.core.tool

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.llm.ChatRequest

interface Tool<in I : ToolInput, out O : ToolOutput> {
    val name: String
    val description: String
    val functions: List<Function>

    data class Function(
        val name: String,
        val description: String,
        val parameters: ChatRequest.Tool.Parameters,
    )

    suspend fun execute(
        input: I
    ): O
}
