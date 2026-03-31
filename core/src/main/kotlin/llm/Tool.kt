package io.github.whiteelephant.autotweaker.core.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * 工具定义。
 * 用于工具调用的功能描述。
 *
 * @property name 函数名称
 * @property description 函数描述
 * @property parameters 函数参数的JSON Schema
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonElement
)
