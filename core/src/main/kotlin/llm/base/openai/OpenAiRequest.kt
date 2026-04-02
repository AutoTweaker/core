package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import io.github.whiteelephant.autotweaker.core.llm.Tool

abstract class OpenAiRequest<Message : OpenAiMessage> {
    abstract val messages: List<Message>
    abstract val model: String
    abstract val temperature: Double?
    abstract val stream: Boolean
    abstract val tools: List<Tool>?
}
