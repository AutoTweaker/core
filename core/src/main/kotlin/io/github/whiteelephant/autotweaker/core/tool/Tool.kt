package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.data.database.settings.SettingItem
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest

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
