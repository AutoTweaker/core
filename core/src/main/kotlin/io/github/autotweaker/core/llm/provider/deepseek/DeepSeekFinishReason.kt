package io.github.autotweaker.core.llm.provider.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeepSeekFinishReason(val value: String) {
    @SerialName("stop")
    STOP("stop"),

    @SerialName("length")
    LENGTH("length"),

    @SerialName("content_filter")
    CONTENT_FILTER("content_filter"),

    @SerialName("tool_calls")
    TOOL_CALLS("tool_calls"),

    @SerialName("insufficient_system_resource")
    INSUFFICIENT_SYSTEM_RESOURCE("insufficient_system_resource")
}
