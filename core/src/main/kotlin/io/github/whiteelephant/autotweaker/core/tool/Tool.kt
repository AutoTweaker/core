package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.llm.ChatRequest
import kotlinx.serialization.json.Json

interface Tool {
    val name: String
    val description: String
    val functions: List<ChatRequest.Tool.Parameters>

    suspend fun execute(
        context: AgentContext,
    ): String
}
