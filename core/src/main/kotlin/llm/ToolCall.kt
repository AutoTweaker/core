package io.github.whiteelephant.autotweaker.core.llm

/**
 * 工具调用。
 * 表示模型决定调用一个工具。
 *
 * @property name 要调用的工具名称
 * @property arguments 调用参数（JSON字符串）
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)
