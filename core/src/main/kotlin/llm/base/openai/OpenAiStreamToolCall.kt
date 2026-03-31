package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OpenAiStreamToolCall(
    val index: Int, // 在流中，index 非常关键，用于识别是哪个工具调用
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiStreamFunctionCall? = null
)

@Serializable
data class OpenAiStreamFunctionCall(
    val name: String? = null,
    val arguments: String? = null // 这里的 arguments 会分多次传过来，比如 "{" -> " \"city\"" -> ": \"西安\"" -> "}"
)
