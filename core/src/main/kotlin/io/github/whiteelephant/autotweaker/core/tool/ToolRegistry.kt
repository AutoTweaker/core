package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import java.util.ServiceLoader

object ToolRegistry {

    private val tools: List<Tool> by lazy {
        ServiceLoader.load(Tool::class.java).toList()
    }

    fun getAllTools(): List<Tool> = tools

    fun toChatRequestTools(): List<io.github.whiteelephant.autotweaker.core.llm.ChatRequest.Tool> {
        return tools.map { tool ->
            io.github.whiteelephant.autotweaker.core.llm.ChatRequest.Tool(
                name = tool.name,
                description = tool.description,
                parameters = tool.functions.first(),
            )
        }
    }

    suspend fun execute(name: String, context: AgentContext): String {
        val tool = tools.find { it.name == name }
            ?: return "Error: tool '$name' not found"
        return tool.execute(context)
    }
}
