package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.agent.llm.AgentContext
import java.util.ServiceLoader

object ToolRegistry {
    private val tools: List<Tool> by lazy {
        ServiceLoader.load(Tool::class.java).toList()
    }

    fun getAllTools(): List<Tool> = tools

    suspend fun execute(name: String, context: AgentContext): String {
        val tool = tools.find { it.name == name }
            ?: throw IllegalArgumentException("Tool '$name' not found")
        return tool.execute(context)
    }
}
