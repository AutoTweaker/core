package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest

interface Tool {
    val name: String
    val functions: List<Function>

    data class Function(
        val name: String,
        val description: String,
        val parameters: ChatRequest.Tool.Parameters,
    )

    suspend fun execute(
        context: AgentContext,
    ): String
}
